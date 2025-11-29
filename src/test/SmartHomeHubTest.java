import devices.*;
import exceptions.ExecutionException;
import exceptions.ValidationException;
import hub.Scene;
import hub.SceneAction;
import hub.SmartHomeHub;
import users.RoleFactory;
import users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Junit test class for SmartHomeHub.
 * covering input validation, execution rollback, device group operations, and permission checks.
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
     * intial setup before each test
     */
    @BeforeEach
    void setUp() {
        hub = new SmartHomeHub();

        // intialize users with different roles
        admin = new User("AdminUser", RoleFactory.createAdminRole());
        parent = new User("ParentUser", RoleFactory.createParentRole());
        child = new User("ChildUser", RoleFactory.createChildRole());

        // initialize devices and register them
        light1 = new SmartLight("Light1");
        thermo1 = new SmartThermostat("Thermo1");
        lock1 = new SmartLock("Lock1");

        hub.registerDevice(admin, light1);
        hub.registerDevice(admin, thermo1);
        hub.registerDevice(admin, lock1);
    }

    // =========================================================================
    // input validation tests for device state updates
    // =========================================================================

    @Test
    void smartLight_ApplyInvalidState_ThrowsValidationException() {
        // case 1: brightness out of range (too high)
        DeviceState invalidState1 = new DeviceState(Map.of("brightness", 150));
        assertThrows(ValidationException.class, () -> {
            light1.applyState(invalidState1);
        }, "brightness value (150) out of range and throw exception.");

        // case 2: brightness out of range (too low)
        DeviceState invalidState2 = new DeviceState(Map.of("brightness", -10));
        assertThrows(ValidationException.class, () -> {
            light1.applyState(invalidState2);
        }, "brightness value (-10) out of range and throw exception.");
    }

    @Test
    void smartLight_ApplyValidState_SucceedsAndImplicitlyTurnsOn() throws ValidationException {
        light1.applyState(new DeviceState(Map.of("power", false))); // ensure light is off

        DeviceState validState = new DeviceState(Map.of("brightness", 50));
        light1.applyState(validState);

        // check the state is updated correctly
        assertEquals(50, light1.getState().get("brightness"));
        // check that the light is turned on implicitly
        assertTrue((Boolean) light1.getState().get("power"), "when setting brightness, power should be set to true implicitly.");
    }

    @Test
    void smartThermostat_ApplyInvalidState_ThrowsValidationException() {
        // case 1: temperature out of range (too high)
        DeviceState invalidState1 = new DeviceState(Map.of("targetTemperature", 31.0));
        assertThrows(ValidationException.class, () -> {
            thermo1.applyState(invalidState1);
        }, "temperature value (31.0) out of range and throw exception.");

        // 边界情况 2：温度超出范围（太低）
        DeviceState invalidState2 = new DeviceState(Map.of("targetTemperature", 4.0));
        assertThrows(ValidationException.class, () -> {
            thermo1.applyState(invalidState2);
        }, "temperature value (4.0) out of range and throw exception.");
    }

    // =========================================================================
    // rollback mechanism tests for scene execution
    // =========================================================================

    @Test
    void executeScene_RollbackOnValidationFailure_RestoresPreviousState() {
        // keep track of original states
        DeviceState originalLightState = light1.getState(); // power=false, brightness=100
        DeviceState originalThermoState = thermo1.getState(); // power=false, targetTemperature=20.0

        // create a scene with mixed success and failure actions
        Scene rollbackScene = new Scene("RollbackTest");

        // action 1 (success): Light1 set to power=true, brightness=50
        DeviceState state1 = new DeviceState(Map.of("power", true, "brightness", 50));
        rollbackScene.addAction(new SceneAction("Light1", state1));

        // action 2 (failure): Thermo1 set to targetTemperature=35.0 (out of range)
        DeviceState state2 = new DeviceState(Map.of("targetTemperature", 35.0));
        rollbackScene.addAction(new SceneAction("Thermo1", state2));

        hub.createScene(admin, rollbackScene);

        // run the scene and expect an ExecutionException due to validation failure
        assertThrows(ExecutionException.class, () -> {
            hub.executeScene(admin, "RollbackTest");
        }, "scene execution must fail and rollback on validation error.");

        // check that Light1's state has been rolled back
        DeviceState finalLightState = light1.getState();
        assertEquals(originalLightState.get("power"), finalLightState.get("power"), "Light1's power must be rolled back.");
        assertEquals(originalLightState.get("brightness"), finalLightState.get("brightness"), "Light1's brightness must be rolled back.");

        // check that Thermo1's state remains unchanged
        DeviceState finalThermoState = thermo1.getState();
        assertEquals(originalThermoState.get("targetTemperature"), finalThermoState.get("targetTemperature"), "Thermo1's temperature must remain unchanged.");
    }


    // =========================================================================
    // device group operation tests
    // =========================================================================

    @Test
    void groupOperations_SuccessAndRollback_Test() throws ExecutionException {
        // create a group with all devices
        hub.createGroup(admin, "AllDevices", List.of("Light1", "Thermo1", "Lock1"));

        // successful group operation
        DeviceState successState = new DeviceState(Map.of("power", true, "locked", false));
        hub.applyToGroup(admin, "AllDevices", successState);

        // check that all devices have been updated
        assertTrue((Boolean) light1.getState().get("power"));
        assertTrue((Boolean) thermo1.getState().get("power"));
        assertFalse((Boolean) lock1.getState().get("locked"));

        // keep track of states before rollback test
        DeviceState preRollbackLightState = light1.getState();
        DeviceState preRollbackLockState = lock1.getState();

        // a failing group operation (brightness out of range)
        DeviceState failingState = new DeviceState(Map.of("brightness", 101));

        assertThrows(ExecutionException.class, () -> {
            hub.applyToGroup(admin, "AllDevices", failingState);
        }, "group operation must fail due to invalid brightness.");

        // check that states have been rolled back
        assertEquals(preRollbackLightState.get("power"), light1.getState().get("power"), "After rollback, Light1's power must be restored.");
        assertEquals(preRollbackLockState.get("locked"), lock1.getState().get("locked"), "After rollback, Lock1's locked state must be restored.");
    }

    // =========================================================================
    // check permission enforcement tests
    // =========================================================================

    @Test
    void hubManagement_UnauthorizedActions_ThrowSecurityException() {
        // when a user lacks necessary permissions for hub management actions
        SmartLight newLight = new SmartLight("NewLight");
        assertThrows(SecurityException.class, () -> {
            hub.registerDevice(parent, newLight);
        }, "Parent does not have REGISTER_DEVICE permission.");

        // when a user lacks necessary permissions for scene creation
        Scene newScene = new Scene("ChildScene");
        assertThrows(SecurityException.class, () -> {
            hub.createScene(child, newScene);
        }, "Child does not have EDIT_SCENES permission.");

        // when a user lacks necessary permissions for group deletion
        hub.createGroup(admin, "TestGroup", List.of());
        assertDoesNotThrow(() -> hub.getGroup("TestGroup"), "ensure group exists before deletion test.");

        assertThrows(SecurityException.class, () -> {
            hub.deleteGroup(child, "TestGroup");
        }, "Child does not have EDIT_GROUPS permission.");
    }

    @Test
    void executeScene_UnauthorizedUser_ThrowsSecurityException() {
        // create a scene as admin
        Scene testScene = new Scene("AuthTestScene");
        testScene.addAction(new SceneAction("Light1", new DeviceState(Map.of("power", true))));
        hub.createScene(admin, testScene);

        // child user tries to execute the scene without EXECUTE_SCENES permission
        assertThrows(SecurityException.class, () -> {
            hub.executeScene(child, "AuthTestScene");
        }, "Child does not have EXECUTE_SCENES permission.");
    }

    @Test
    void deviceManagement_DeregisterNonExistentDevice_NoException() {
        // try to deregister a device that does not exist
        assertDoesNotThrow(() -> {
            hub.deregisterDevice(admin, "NonExistentDevice");
        }, "when deregistering a non-existent device, no exception should be thrown.");

        // check that existing devices are unaffected
        assertTrue(hub.getDevice("Light1").isPresent(), "existing device Light1 should still be present.");
    }

    @Test
    void deviceManagement_RegisterDuplicateDevice_OverwritesExisting() {
        SmartLight newLight = new SmartLight("Light1"); // use the same name as existing device
        hub.registerDevice(admin, newLight);

        // check that the device has been overwritten
        Device retrievedDevice = hub.getDevice("Light1").orElseThrow();
        assertNotSame(light1, retrievedDevice, "Registering a device with duplicate name should overwrite existing device.");
        assertSame(newLight, retrievedDevice, "should retrieve the newly registered device.");
    }

    @Test
    void executeScene_MissingDevice_ThrowsExecutionExceptionAndRollsBack() throws ValidationException {
        // keep track of original states
        DeviceState originalLightState = light1.getState();

        // change Light1 state to ON
        DeviceState turnOn = new DeviceState(Map.of("power", true));
        light1.applyState(turnOn);
        assertTrue((Boolean) light1.getState().get("power"), "Light1 should be ON before scene execution.");

        // deregister Thermo1 to simulate missing device
        hub.deregisterDevice(admin, "Thermo1");

        // create a scene that includes the missing device
        Scene missingDeviceScene = new Scene("MissingDeviceScene");
        missingDeviceScene.addAction(new SceneAction("Light1", new DeviceState(Map.of("brightness", 50))));
        missingDeviceScene.addAction(new SceneAction("Thermo1", new DeviceState(Map.of("targetTemperature", 25.0))));
        hub.createScene(admin, missingDeviceScene);

        // execute the scene and expect an ExecutionException due to missing device
        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            hub.executeScene(admin, "MissingDeviceScene");
        }, "scene execution fail due to missing device Thermo1.");

        // check that the exception message indicates the missing device
        assertTrue(ex.getMessage().contains("device missing Thermo1"), "exception message should indicate missing device.");

        // check that Light1's state has been rolled back to original
        DeviceState finalLightState = light1.getState();
        assertTrue((Boolean) finalLightState.get("power"), "Light1 should remain ON after rollback.");
        assertEquals(originalLightState.get("brightness"), finalLightState.get("brightness"), "Light1's brightness should be rolled back.");


    }

    @Test
    void role_CanControlDeviceLogic_IsAccurate() {
        // Admin should have all permissions
        assertTrue(admin.getRole().canControlDevice("LIGHT"), "Admin should have permission for LIGHT.");
        assertTrue(admin.getRole().canControlDevice("UNKNOWN"), "Admin should have permission for all device types.");

        // Child only has CONTROL_LIGHTS permission
        assertTrue(child.getRole().canControlDevice("LIGHT"), "Child should have permission for LIGHT.");
        assertFalse(child.getRole().canControlDevice("LOCK"), "Child should not have permission for LOCK.");
        assertFalse(child.getRole().canControlDevice("THERMOSTAT"), "Child should not have permission for THERMOSTAT.");

        // check unknown device type for Parent
        assertFalse(parent.getRole().canControlDevice("UNKNOWN_DEVICE_TYPE"), "Normal roles should not have permission for unknown device types.");
    }

    @Test
    void applyToGroup_UserLacksSomePermissions_SkipsUnauthorizedDevices() throws ExecutionException, ValidationException {
        // child only has permission to control Lights

        //create a group with mixed device types
        hub.createGroup(admin, "MixedGroup", List.of("Light1", "Thermo1", "Lock1"));

        // keep track of original states
        DeviceState originalThermoState = thermo1.getState();
        DeviceState originalLockState = lock1.getState();

        // set desired state to apply
        DeviceState applyState = new DeviceState();
        applyState.set("power", true);
        applyState.set("locked", false);

        // child executes group operation, should skip unauthorized devices
        hub.applyToGroup(child, "MixedGroup", applyState);

        // check results of light1 (has permission)
        assertTrue((Boolean) light1.getState().get("power"), "Light1's power should be set to true by Child.");

        // check thermo1 (no permission) state unchanged
        assertEquals(originalThermoState.get("power"), thermo1.getState().get("power"), "Thermo1 should remain unchanged due to lack of permission.");

        // check lock1 (no permission) state unchanged
        assertEquals(originalLockState.get("locked"), lock1.getState().get("locked"), "Lock1 should remain unchanged due to lack of permission.");
    }

    @Test
    void deviceState_ExternalModification_DoesNotAffectInternalState() throws ValidationException {
        // get the current state of light1( clone it)
        DeviceState stateClone = light1.getState();

        // try to modify the cloned state
        stateClone.set("power", true);
        stateClone.set("newKey", 123);

        // check that the internal state of light1 is unaffected
        assertFalse((Boolean) light1.getState().get("power"), "change to cloned state should not affect device internal state.");
        assertNull(light1.getState().get("newKey"), "In ternal state should not have newKey added.");

        // check that asMap() returns unmodifiable map
        assertThrows(UnsupportedOperationException.class, () -> {
            stateClone.asMap().put("attemptedModification", 456);
        }, "asMap() must return unmodifiable map.");
    }

    @Test
    void scene_GetActions_ReturnsUnmodifiableList() {
        // create a scene with one action
        Scene scene = new Scene("DefensiveScene");
        SceneAction action = new SceneAction("Light1", new DeviceState());
        scene.addAction(action);

        // get the actions list
        List<SceneAction> actions = scene.getActions();

        // try to modify the actions list
        assertThrows(UnsupportedOperationException.class, () -> {
            actions.add(new SceneAction("Thermo1", new DeviceState()));
        }, "getActions() must return unmodifiable list.");
    }

    /**
     * check applying invalid data types to device states
     */

    @Test
    void smartLight_ApplyInvalidType_ThrowsValidationException() {
        // Light: power must be Boolean
        DeviceState invalidTypeState1 = new DeviceState(Map.of("power", "ON")); // String
        assertThrows(ValidationException.class, () -> {
            light1.applyState(invalidTypeState1);
        }, "power must be Boolean，if pass String should fail.");

        // Light: brightness 必须是 Number
        DeviceState invalidTypeState2 = new DeviceState(Map.of("brightness", "High")); // String
        assertThrows(ValidationException.class, () -> {
            light1.applyState(invalidTypeState2);
        }, "brightness must be Number，if pass String should fail.");
    }

    @Test
    void smartThermostat_ApplyInvalidType_ThrowsValidationException() {
        // Thermostat: power must be Boolean
        DeviceState invalidTypeState1 = new DeviceState(Map.of("power", 1)); // Integer
        assertThrows(ValidationException.class, () -> {
            thermo1.applyState(invalidTypeState1);
        }, "power must be Boolean, if pass Integer should fail.");

        // Thermostat: targetTemperature must be Number
        DeviceState invalidTypeState2 = new DeviceState(Map.of("targetTemperature", false)); // Boolean
        assertThrows(ValidationException.class, () -> {
            thermo1.applyState(invalidTypeState2);
        }, "targetTemperature must be Number, if pass Boolean should fail.");
    }

    @Test
    void smartLock_ApplyInvalidType_ThrowsValidationException() {
        // Lock: locked must be Boolean
        DeviceState invalidTypeState = new DeviceState(Map.of("locked", 0)); // Integer
        assertThrows(ValidationException.class, () -> {
            lock1.applyState(invalidTypeState);
        }, "locked must be Boolean, if pass Integer should fail.");
    }

    @Test
    void applyToGroup_MissingDevice_IsSkippedSuccessfully() throws ExecutionException, ValidationException {
        // Light1 power=false
        DeviceState originalLightState = light1.getState();

        // deregister Thermo1 to simulate missing device
        hub.deregisterDevice(admin, "Thermo1");

        // creat e a group with Light1 and missing Thermo1
        hub.createGroup(admin, "TestSkipGroup", List.of("Light1", "Thermo1"));

        // apply state to turn on power
        DeviceState applyState = new DeviceState(Map.of("power", true));

        // excute group operation
        assertDoesNotThrow(() -> {
            hub.applyToGroup(admin, "TestSkipGroup", applyState);
        }, "group operation should succeed, skipping missing devices.");

        // check that Light1 is updated
        assertTrue((Boolean) light1.getState().get("power"), "existing device Light1 should be updated.");

        // check that Thermo1 is still missing
        assertTrue(hub.getDevice("Thermo1").isEmpty(), "the missing device Thermo1 should remain unregistered.");
    }
}

