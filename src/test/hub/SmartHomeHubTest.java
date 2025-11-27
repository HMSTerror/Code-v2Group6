package hub;

import devices.*;
import exceptions.ExecutionException;
import exceptions.ValidationException;
import users.RoleFactory;
import users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 智能家居集线器（SmartHomeHub）的 JUnit 测试套件。
 * 覆盖了核心功能、权限控制、输入验证和场景执行回滚等边界情况。
 */
public class SmartHomeHubTest {
    private SmartHomeHub hub;
    private User admin;
    private User parent;
    private User child;

    private SmartLight light1;
    private SmartThermostat thermo1;
    private SmartLock lock1;

    /**
     * 在每个测试方法执行前初始化集线器、用户和设备。
     */
    @BeforeEach
    void setUp() {
        hub = new SmartHomeHub();

        // 初始化用户角色
        admin = new User("AdminUser", RoleFactory.createAdminRole());
        parent = new User("ParentUser", RoleFactory.createParentRole());
        child = new User("ChildUser", RoleFactory.createChildRole());

        // 初始化并注册设备
        light1 = new SmartLight("Light1");
        thermo1 = new SmartThermostat("Thermo1");
        lock1 = new SmartLock("Lock1");

        hub.registerDevice(admin, light1);
        hub.registerDevice(admin, thermo1);
        hub.registerDevice(admin, lock1);
    }

    // =========================================================================
    // 1. 输入验证和边界情况测试 (覆盖 ValidationException)
    // =========================================================================

    @Test
    void smartLight_ApplyInvalidState_ThrowsValidationException() {
        // 边界情况 1：亮度超出范围（太高）
        DeviceState invalidState1 = new DeviceState(Map.of("brightness", 150));
        assertThrows(ValidationException.class, () -> {
            light1.applyState(invalidState1);
        }, "亮度值 (150) 必须超出范围 (0-100) 并抛出异常。");

        // 边界情况 2：亮度超出范围（负数）
        DeviceState invalidState2 = new DeviceState(Map.of("brightness", -10));
        assertThrows(ValidationException.class, () -> {
            light1.applyState(invalidState2);
        }, "亮度值 (-10) 必须超出范围并抛出异常。");
    }

    @Test
    void smartLight_ApplyValidState_SucceedsAndImplicitlyTurnsOn() throws ValidationException {
        light1.applyState(new DeviceState(Map.of("power", false))); // 先确保关机

        DeviceState validState = new DeviceState(Map.of("brightness", 50));
        light1.applyState(validState);

        // 验证：亮度更新成功
        assertEquals(50, light1.getState().get("brightness"));
        // 验证：设置亮度时，设备必须自动开启（隐式行为）
        assertTrue((Boolean) light1.getState().get("power"), "设置亮度时必须自动开机。");
    }

    @Test
    void smartThermostat_ApplyInvalidState_ThrowsValidationException() {
        // 边界情况 1：温度超出范围（太高）
        DeviceState invalidState1 = new DeviceState(Map.of("targetTemperature", 31.0));
        assertThrows(ValidationException.class, () -> {
            thermo1.applyState(invalidState1);
        }, "温度值 (31.0) 必须超出范围并抛出异常。");

        // 边界情况 2：温度超出范围（太低）
        DeviceState invalidState2 = new DeviceState(Map.of("targetTemperature", 4.0));
        assertThrows(ValidationException.class, () -> {
            thermo1.applyState(invalidState2);
        }, "温度值 (4.0) 必须超出范围并抛出异常。");
    }

    // =========================================================================
    // 2. 场景执行回滚测试 (覆盖 ExecutionException 和原子性)
    // =========================================================================

    @Test
    void executeScene_RollbackOnValidationFailure_RestoresPreviousState() {
        // 1. 记录初始状态
        DeviceState originalLightState = light1.getState(); // power=false, brightness=100
        DeviceState originalThermoState = thermo1.getState(); // power=false, targetTemperature=20.0

        // 2. 创建场景：第一个动作成功，第二个动作失败
        Scene rollbackScene = new Scene("RollbackTest");

        // 动作 1 (成功): Light1 开启并设置亮度为 50
        DeviceState state1 = new DeviceState(Map.of("power", true, "brightness", 50));
        rollbackScene.addAction(new SceneAction("Light1", state1));

        // 动作 2 (失败): Thermo1 温度超出上限 (35.0 > 30.0)
        DeviceState state2 = new DeviceState(Map.of("targetTemperature", 35.0));
        rollbackScene.addAction(new SceneAction("Thermo1", state2));

        hub.createScene(admin, rollbackScene);

        // 3. 执行并预期抛出 ExecutionException (包含 ValidationException)
        assertThrows(ExecutionException.class, () -> {
            hub.executeScene(admin, "RollbackTest");
        }, "场景执行必须因验证失败而中断。");

        // 4. 验证回滚：Light1 的状态必须恢复到场景执行前的状态
        DeviceState finalLightState = light1.getState();
        assertEquals(originalLightState.get("power"), finalLightState.get("power"), "Light1's power 必须回滚。");
        assertEquals(originalLightState.get("brightness"), finalLightState.get("brightness"), "Light1's brightness 必须回滚。");

        // 5. 验证：Thermo1 的状态也应该保持不变
        DeviceState finalThermoState = thermo1.getState();
        assertEquals(originalThermoState.get("targetTemperature"), finalThermoState.get("targetTemperature"), "Thermo1 的状态不应被改变。");
    }


    // =========================================================================
    // 3. 设备组操作测试
    // =========================================================================

    @Test
    void groupOperations_SuccessAndRollback_Test() throws ExecutionException {
        // 1. 创建群组
        hub.createGroup(admin, "AllDevices", List.of("Light1", "Thermo1", "Lock1"));

        // 2. 成功的群组操作
        DeviceState successState = new DeviceState(Map.of("power", true, "locked", false));
        hub.applyToGroup(admin, "AllDevices", successState);

        // 验证状态更新
        assertTrue((Boolean) light1.getState().get("power"));
        assertTrue((Boolean) thermo1.getState().get("power"));
        assertFalse((Boolean) lock1.getState().get("locked"));

        // 3. 记录群组操作前的状态
        DeviceState preRollbackLightState = light1.getState();
        DeviceState preRollbackLockState = lock1.getState();

        // 4. 失败的群组操作（设置 Light1 的亮度超出范围）
        DeviceState failingState = new DeviceState(Map.of("brightness", 101));

        assertThrows(ExecutionException.class, () -> {
            hub.applyToGroup(admin, "AllDevices", failingState);
        }, "群组操作必须因验证错误而回滚。");

        // 5. 验证回滚：Light1 和 Lock1 的状态必须恢复
        assertEquals(preRollbackLightState.get("power"), light1.getState().get("power"), "回滚后 Light1 的 Power 必须恢复。");
        assertEquals(preRollbackLockState.get("locked"), lock1.getState().get("locked"), "回滚后 Lock1 的状态必须恢复。");
    }

    // =========================================================================
    // 4. 权限检查测试 (覆盖 SecurityException)
    // =========================================================================

    @Test
    void hubManagement_UnauthorizedActions_ThrowSecurityException() {
        // 场景 1：非 Admin 尝试注册新设备 (需要 REGISTER_DEVICE)
        SmartLight newLight = new SmartLight("NewLight");
        assertThrows(SecurityException.class, () -> {
            hub.registerDevice(parent, newLight);
        }, "Parent 缺乏 REGISTER_DEVICE 权限。");

        // 场景 2：Child 尝试创建场景 (需要 EDIT_SCENES)
        Scene newScene = new Scene("ChildScene");
        assertThrows(SecurityException.class, () -> {
            hub.createScene(child, newScene);
        }, "Child 缺乏 EDIT_SCENES 权限。");

        // 场景 3：Parent 尝试删除群组 (需要 EDIT_GROUPS)
        hub.createGroup(admin, "TestGroup", List.of());
        assertDoesNotThrow(() -> hub.getGroup("TestGroup"), "确保群组已创建。");

        assertThrows(SecurityException.class, () -> {
            hub.deleteGroup(child, "TestGroup");
        }, "Child 缺乏 EDIT_GROUPS 权限。");
    }

    @Test
    void executeScene_UnauthorizedUser_ThrowsSecurityException() {
        // 创建一个场景
        Scene testScene = new Scene("AuthTestScene");
        testScene.addAction(new SceneAction("Light1", new DeviceState(Map.of("power", true))));
        hub.createScene(admin, testScene);

        // Child 缺乏 EXECUTE_SCENES 权限 (参见 RoleFactory)
        assertThrows(SecurityException.class, () -> {
            hub.executeScene(child, "AuthTestScene");
        }, "Child 缺乏 EXECUTE_SCENES 权限。");
    }

    @Test
    void deviceManagement_DeregisterNonExistentDevice_NoException() {
        // 尝试注销一个不存在的设备名称，系统不应抛出异常，并安全完成操作。
        assertDoesNotThrow(() -> {
            hub.deregisterDevice(admin, "NonExistentDevice");
        }, "注销不存在的设备时不应抛出异常。");

        // 验证 Light1 仍然存在（未被意外删除）
        assertTrue(hub.getDevice("Light1").isPresent(), "现有设备不应被意外删除。");
    }

    @Test
    void deviceManagement_RegisterDuplicateDevice_OverwritesExisting() {
        SmartLight newLight = new SmartLight("Light1"); // 使用相同的名称
        hub.registerDevice(admin, newLight);

        // 验证：集线器中存储的设备实例是否被新的 Light 实例替换
        Device retrievedDevice = hub.getDevice("Light1").orElseThrow();
        assertNotSame(light1, retrievedDevice, "重复注册的设备实例应该被新的对象替换。");
        assertSame(newLight, retrievedDevice, "集线器应存储新的设备实例。");
    }

    @Test
    void executeScene_MissingDevice_ThrowsExecutionExceptionAndRollsBack() throws ValidationException {
        // 1. 记录初始状态：Light1 状态为默认 (power=false)
        DeviceState originalLightState = light1.getState();

        // 2. 更改 Light1 状态 (第一个成功动作)
        DeviceState turnOn = new DeviceState(Map.of("power", true));
        light1.applyState(turnOn);
        assertTrue((Boolean) light1.getState().get("power"), "Light1 状态应为 ON。");

        // 3. 注销一个设备 (使其在场景执行时缺失)
        hub.deregisterDevice(admin, "Thermo1");

        // 4. 创建场景：Light1 成功操作 -> Thermo1 缺失操作（失败）
        Scene missingDeviceScene = new Scene("MissingDeviceScene");
        missingDeviceScene.addAction(new SceneAction("Light1", new DeviceState(Map.of("brightness", 50))));
        missingDeviceScene.addAction(new SceneAction("Thermo1", new DeviceState(Map.of("targetTemperature", 25.0))));
        hub.createScene(admin, missingDeviceScene);

        // 5. 执行并预期抛出 ExecutionException (设备未找到)
        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            hub.executeScene(admin, "MissingDeviceScene");
        }, "场景执行必须因设备缺失而中断。");

        // 6. 验证异常信息
        assertTrue(ex.getMessage().contains("device missing Thermo1"), "异常信息应指明缺失的设备。");

        // 7. 验证回滚：Light1 的状态必须恢复到场景执行前的状态 (即步骤 2 的 ON 状态)
        DeviceState finalLightState = light1.getState();
        assertTrue((Boolean) finalLightState.get("power"), "Light1 的状态应回滚到 ON。");
        assertEquals(originalLightState.get("brightness"), finalLightState.get("brightness"), "Light1 的亮度应回滚到原始值。");


    }

    @Test
    void role_CanControlDeviceLogic_IsAccurate() {
        // Admin 角色：应能控制所有设备
        assertTrue(admin.getRole().canControlDevice("LIGHT"), "Admin 应对 LIGHT 有权限。");
        assertTrue(admin.getRole().canControlDevice("UNKNOWN"), "Admin 应对所有设备类型有权限。");

        // Child 角色：仅有 LIGHT 权限
        assertTrue(child.getRole().canControlDevice("LIGHT"), "Child 应对 LIGHT 有权限。");
        assertFalse(child.getRole().canControlDevice("LOCK"), "Child 不应对 LOCK 有权限。");
        assertFalse(child.getRole().canControlDevice("THERMOSTAT"), "Child 不应对 THERMOSTAT 有权限。");

        // 检查未知设备类型：所有非 CONTROL_ALL_DEVICES 的角色，都应该对未知类型返回 false
        assertFalse(parent.getRole().canControlDevice("UNKNOWN_DEVICE_TYPE"), "非 Admin 角色不应对未知设备类型有权限。");
    }

    @Test
    void applyToGroup_UserLacksSomePermissions_SkipsUnauthorizedDevices() throws ExecutionException, ValidationException {
        // 1. Child 用户的权限：只有 CONTROL_LIGHTS 和 VIEW_STATUS
        //    Child 不具备控制 Lock 和 Thermostat 的权限。

        // 2. 创建一个包含所有设备类型的群组
        hub.createGroup(admin, "MixedGroup", List.of("Light1", "Thermo1", "Lock1"));

        // 3. 记录设备初始状态
        DeviceState originalThermoState = thermo1.getState(); // 期望 Thermo1 不变
        DeviceState originalLockState = lock1.getState();     // 期望 Lock1 不变

        // 4. 定义应用状态：Light: ON, Lock: UNLOCKED, Thermo: ON
        DeviceState applyState = new DeviceState();
        applyState.set("power", true);
        applyState.set("locked", false); // Lock1 的状态

        // 5. Child 执行群组操作：应该跳过 Lock1 和 Thermo1，只成功应用 Light1
        hub.applyToGroup(child, "MixedGroup", applyState);

        // 6. 验证 Light1 (有权限) 成功应用状态
        assertTrue((Boolean) light1.getState().get("power"), "Light1 (Child有权限) 状态应为 ON。");

        // 7. 验证 Thermo1 (无权限) 状态未改变 (被跳过)
        assertEquals(originalThermoState.get("power"), thermo1.getState().get("power"), "Thermo1 (Child无权限) 状态应被跳过，保持不变。");

        // 8. 验证 Lock1 (无权限) 状态未改变 (被跳过)
        assertEquals(originalLockState.get("locked"), lock1.getState().get("locked"), "Lock1 (Child无权限) 状态应被跳过，保持不变。");
    }

    @Test
    void deviceState_ExternalModification_DoesNotAffectInternalState() throws ValidationException {
        // 1. 获取 Light1 的状态 (clone())
        DeviceState stateClone = light1.getState();

        // 2. 尝试修改这个克隆对象
        stateClone.set("power", true);
        stateClone.set("newKey", 123);

        // 3. 验证 Light1 的实际内部状态没有被修改
        assertFalse((Boolean) light1.getState().get("power"), "修改克隆对象不应影响设备实际状态（封装）。");
        assertNull(light1.getState().get("newKey"), "设备内部不应有新的键值。");

        // 4. 验证 asMap() 返回的 Map 是不可修改的
        assertThrows(UnsupportedOperationException.class, () -> {
            stateClone.asMap().put("attemptedModification", 456);
        }, "asMap() 必须返回不可修改的 Map。");
    }

    @Test
    void scene_GetActions_ReturnsUnmodifiableList() {
        // 1. 创建场景并添加动作
        Scene scene = new Scene("DefensiveScene");
        SceneAction action = new SceneAction("Light1", new DeviceState());
        scene.addAction(action);

        // 2. 获取动作列表
        List<SceneAction> actions = scene.getActions();

        // 3. 尝试向返回的列表中添加新的动作
        assertThrows(UnsupportedOperationException.class, () -> {
            actions.add(new SceneAction("Thermo1", new DeviceState()));
        }, "getActions() 必须返回不可修改的列表（防止外部破坏场景）。");
    }
}

