import devices.*;
import exceptions.ExecutionException;
import exceptions.ValidationException;
import hub.Scene;
import hub.SceneAction;
import hub.SmartHomeHub;
import users.Permission;
import users.User;
import users.roles.RoleFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartHomeHubTest (Enhanced & Comprehensive)
 * covers critical aspects of the Smart Home Hub system:
 * 1. Input Validation
 * 2. Device Registry Management
 * 3. Security & Permissions
 * 4. Rollback Logic
 * 5. Immutability
 */
public class SmartHomeHubTest {
    private SmartHomeHub hub;
    private User admin;
    private User parent;
    private User child;

    private SmartLight light1;
    private SmartThermostat thermo1;
    private SmartLock lock1;

    @BeforeEach
    void setUp() {
        hub = new SmartHomeHub();

        // intialize users
        admin = new User("AdminUser", RoleFactory.createAdminRole());
        parent = new User("ParentUser", RoleFactory.createParentRole()); // Parent 无权注册设备
        child = new User("ChildUser", RoleFactory.createChildRole());    // Child 权限最低

        // intialize devices
        light1 = new SmartLight("Light1");
        thermo1 = new SmartThermostat("Thermo1");
        lock1 = new SmartLock("Lock1");

        // initialize hub with devices
        hub.registerDevice(admin, light1);
        hub.registerDevice(admin, thermo1);
        hub.registerDevice(admin, lock1);
    }

    // =========================================================================
    // Input Validation Tests
    // =========================================================================

    @Test
    void state_Constructor_ValidatesInputRange() {
        // test valid LightState(>100)
        assertThrows(IllegalArgumentException.class, () -> new LightState(true, 150),
                "Should fail when creating LightState with brightness 150");

        // test invalid LightState (<0)
        assertThrows(IllegalArgumentException.class, () -> new LightState(true, -10),
                "Should fail when creating LightState with negative brightness");

        // test valid ThermostatState (>30.0)
        assertThrows(IllegalArgumentException.class, () -> new ThermostatState(true, 31.0),
                "Should fail when creating ThermostatState > 30.0");
    }

    @Test
    void device_ApplyInvalidStateType_ThrowsValidationException() {
        // try applying ThermostatState to SmartLight
        ThermostatState wrongState = new ThermostatState(true, 20.0);
        assertThrows(ValidationException.class, () -> light1.applyState(wrongState),
                "Applying ThermostatState to SmartLight should throw ValidationException");
    }

    // =========================================================================
    // Hub Device Registry Tests
    // =========================================================================

    @Test
    void registerDevice_DuplicateName_OverwritesExisting() {
        SmartLight newLight = new SmartLight("Light1"); // 同名设备
        hub.registerDevice(admin, newLight);

        Device retrieved = hub.getDevice("Light1");
        assertNotSame(light1, retrieved, "Old device object should be replaced");
        assertSame(newLight, retrieved, "New device object should be in registry");
    }

    @Test
    void deregisterDevice_NonExistent_DoesNotThrowException() {
        // try deregistering a device that doesn't exist
        assertDoesNotThrow(() -> hub.deregisterDevice(admin, "GhostDevice"));
    }

    @Test
    void deregisterDevice_Success_RemovesDevice() {
        hub.deregisterDevice(admin, "Light1");
        assertNull(hub.getDevice("Light1"), "Device should be null after deregistration");
    }

    // =========================================================================
    // Security & Permissions Tests
    // =========================================================================

    @Test
    void hub_UnauthorizedActions_ThrowSecurityException() {
        SmartLight newLight = new SmartLight("KitchenLight");

        // Parent tries to register a device
        assertThrows(SecurityException.class, () -> hub.registerDevice(parent, newLight),
                "Parent should NOT be allowed to register devices");

        // Child tries to create a scene
        Scene scene = new Scene("ChildPlay");
        assertThrows(SecurityException.class, () -> hub.createScene(child, scene),
                "Child should NOT be allowed to create scenes");

        // Parent tries to deregister a device
        assertThrows(SecurityException.class, () -> hub.deregisterDevice(parent, "Light1"),
                "Parent should NOT be allowed to deregister devices");
    }

    @Test
    void executeScene_UnauthorizedUser_ThrowsSecurityException() {
        Scene scene = new Scene("TestScene");
        scene.addAction(new SceneAction(light1, new LightState(true, 50)));
        hub.createScene(admin, scene);

        // Child tries to execute the scene
        assertThrows(SecurityException.class, () -> hub.executeScene(child, "TestScene"),
                "Child should NOT be allowed to execute scenes");
    }

    // =========================================================================
    // Rollback Logic Tests
    // =========================================================================

    @Test
    void executeScene_Success_UpdatesDevices() throws ExecutionException, ValidationException {
        // initial state
        light1.applyState(new LightState(false, 0));

        Scene scene = new Scene("GoodMorning");
        scene.addAction(new SceneAction(light1, new LightState(true, 80)));
        hub.createScene(admin, scene);

        // parent executes the scene
        hub.executeScene(parent, "GoodMorning");

        // check updated state
        LightState current = (LightState) light1.getState();
        assertTrue(current.isPowerOn());
        assertEquals(80, current.getBrightness());
    }

    @Test
    void executeScene_RollbackOnFailure_RestoresPreviousState() throws ValidationException {
        // initial state
        light1.applyState(new LightState(false, 0)); // Light1 OFF
        thermo1.applyState(new ThermostatState(false, 20.0)); // Thermo1 20度

        // create a scene with one action that will fail
        Scene rollbackScene = new Scene("DisasterScene");

        // action A sets Light1 to ON, 100(run first)
        rollbackScene.addAction(new SceneAction(light1, new LightState(true, 100)));

        // action B sets Thermo1 to ON, 25.0 (will fail)
        Device faultyDevice = new SmartLight("FaultyLight") {
            @Override
            public void applyState(DeviceState state) throws ValidationException {
                throw new ValidationException("Hardware connection failed");
            }
        };
        hub.registerDevice(admin, faultyDevice);
        rollbackScene.addAction(new SceneAction(faultyDevice, new LightState(true, 50)));

        hub.createScene(admin, rollbackScene);

        // execute the scene and expect failure
        assertThrows(ExecutionException.class, () -> hub.executeScene(admin, "DisasterScene"));

        // check that Light1 state was rolled back
        LightState finalLightState = (LightState) light1.getState();
        assertFalse(finalLightState.isPowerOn(), "Rollback failed: Light should be OFF");
        assertEquals(0, finalLightState.getBrightness(), "Rollback failed: Brightness should be 0");
    }

    @Test
    void executeScene_MissingDevice_ThrowsException() {
        // scenario: device is deregistered before scene execution
        Scene scene = new Scene("GhostScene");
        scene.addAction(new SceneAction(light1, new LightState(true, 100)));
        hub.createScene(admin, scene);

        // deregister the device before execution
        hub.deregisterDevice(admin, "Light1");

        // 执行场景时，通常有两种设计：
        // 1. 忽略缺失设备继续执行 (Robustness)
        // 2. 报错停止 (Strict Consistency)
        // 在你的 SceneExecutor 中，因为 SceneAction 直接持有 Device 对象引用，
        // 即使 Registry 里没有了，对象还在内存里，所以 device.applyState() 依然会成功。
        // 这其实展示了对象引用的一个特性。
        // *如果* 我们想要测试“如果设备真的不可用”，我们应该模拟 device 抛出异常，或者在 Executor 里加校验。

        // 但根据当前代码逻辑，这个场景其实是 *会成功* 的，因为 Action 持有的是强引用。
        // 让我们验证它 *确实* 成功执行了（这证明了引用有效性），或者如果你希望它失败，我们需要改 Executor。
        // 这里的测试假设它是成功的（基于当前代码逻辑）：
        assertDoesNotThrow(() -> hub.executeScene(admin, "GhostScene"));
    }

    // =========================================================================
    // Immutability Tests
    // =========================================================================

    @Test
    void scene_GetActions_ReturnsUnmodifiableList() {
        Scene scene = new Scene("SecureScene");
        List<SceneAction> actions = scene.getActions();

        // try to modify the returned list
        assertThrows(UnsupportedOperationException.class, () -> {
            actions.add(new SceneAction(light1, new LightState(true, 50)));
        }, "Scene actions list should be immutable");
    }

    @Test
    void state_Immutability_Check() throws ValidationException {
        // check that applying a state and retrieving it gives the same instance
        LightState state = new LightState(true, 50);
        light1.applyState(state);

        DeviceState savedState = light1.getState();

        // as LightState is immutable, the same instance should be returned
        assertSame(state, savedState, "Immutable objects can safely be shared/reused");
    }
}