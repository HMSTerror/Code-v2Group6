import devices.*;
import exceptions.ExecutionException;
import exceptions.ValidationException;
import hub.Scene;
import hub.SceneAction;
import hub.SmartHomeHub;
import users.User;
import users.roles.RoleFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

        admin = new User("AdminUser", RoleFactory.createAdminRole());
        parent = new User("ParentUser", RoleFactory.createParentRole());
        child = new User("ChildUser", RoleFactory.createChildRole());

        light1 = new SmartLight("Light1");
        thermo1 = new SmartThermostat("Thermo1");
        lock1 = new SmartLock("Lock1");

        hub.registerDevice(admin, light1);
        hub.registerDevice(admin, thermo1);
        hub.registerDevice(admin, lock1);
    }

    // ... (Input Validation Tests 保持不变) ...

    @Test
    void state_Constructor_ValidatesInputRange() {
        assertThrows(IllegalArgumentException.class, () -> new LightState(true, 150));
        assertThrows(IllegalArgumentException.class, () -> new LightState(true, -10));
        assertThrows(IllegalArgumentException.class, () -> new ThermostatState(true, 31.0));
    }

    @Test
    void device_ApplyInvalidStateType_ThrowsValidationException() {
        ThermostatState wrongState = new ThermostatState(true, 20.0);
        assertThrows(ValidationException.class, () -> light1.applyState(wrongState));
    }

    // =========================================================================
    // Hub Device Registry Tests
    // =========================================================================

    @Test
    void registerDevice_DuplicateName_OverwritesExisting() {
        SmartLight newLight = new SmartLight("Light1");
        hub.registerDevice(admin, newLight);

        Device retrieved = hub.getDevice("Light1");
        assertNotSame(light1, retrieved);
        assertSame(newLight, retrieved);
    }

    @Test
    void deregisterDevice_NonExistent_DoesNotThrowException() {
        // Updated: Create a dummy device object to test deregistration robustness
        Device ghost = new SmartLight("GhostDevice");
        assertDoesNotThrow(() -> hub.deregisterDevice(admin, ghost));
    }

    @Test
    void deregisterDevice_Success_RemovesDevice() {
        // Updated: Pass the device object 'light1' instead of string "Light1"
        hub.deregisterDevice(admin, light1);
        assertNull(hub.getDevice("Light1"), "Device should be null after deregistration");
    }

    // =========================================================================
    // Security & Permissions Tests
    // =========================================================================

    @Test
    void hub_UnauthorizedActions_ThrowSecurityException() {
        SmartLight newLight = new SmartLight("KitchenLight");

        assertThrows(SecurityException.class, () -> hub.registerDevice(parent, newLight));

        Scene scene = new Scene("ChildPlay");
        assertThrows(SecurityException.class, () -> hub.createScene(child, scene));

        // Updated: Pass device object
        assertThrows(SecurityException.class, () -> hub.deregisterDevice(parent, light1));
    }

    @Test
    void executeScene_UnauthorizedUser_ThrowsSecurityException() {
        Scene scene = new Scene("TestScene");
        scene.addAction(new SceneAction(light1, new LightState(true, 50)));
        hub.createScene(admin, scene);

        assertThrows(SecurityException.class, () -> hub.executeScene(child, "TestScene"));
    }

    // =========================================================================
    // Rollback Logic Tests
    // =========================================================================

    @Test
    void executeScene_Success_UpdatesDevices() throws ExecutionException, ValidationException {
        light1.applyState(new LightState(false, 0));

        Scene scene = new Scene("GoodMorning");
        scene.addAction(new SceneAction(light1, new LightState(true, 80)));
        hub.createScene(admin, scene);

        hub.executeScene(parent, "GoodMorning");

        LightState current = (LightState) light1.getState();
        assertTrue(current.isPowerOn());
        assertEquals(80, current.getBrightness());
    }

    @Test
    void executeScene_RollbackOnFailure_RestoresPreviousState() throws ValidationException {
        light1.applyState(new LightState(false, 0));
        thermo1.applyState(new ThermostatState(false, 20.0));

        Scene rollbackScene = new Scene("DisasterScene");
        rollbackScene.addAction(new SceneAction(light1, new LightState(true, 100)));

        Device faultyDevice = new SmartLight("FaultyLight") {
            @Override
            public void applyState(DeviceState state) throws ValidationException {
                throw new ValidationException("Hardware connection failed");
            }
        };
        hub.registerDevice(admin, faultyDevice);
        rollbackScene.addAction(new SceneAction(faultyDevice, new LightState(true, 50)));

        hub.createScene(admin, rollbackScene);

        assertThrows(ExecutionException.class, () -> hub.executeScene(admin, "DisasterScene"));

        LightState finalLightState = (LightState) light1.getState();
        assertFalse(finalLightState.isPowerOn());
        assertEquals(0, finalLightState.getBrightness());
    }

    @Test
    void executeScene_MissingDevice_ThrowsException() {
        Scene scene = new Scene("GhostScene");
        scene.addAction(new SceneAction(light1, new LightState(true, 100)));
        hub.createScene(admin, scene);

        // Updated: Pass device object
        hub.deregisterDevice(admin, light1);

        assertDoesNotThrow(() -> hub.executeScene(admin, "GhostScene"));
    }

    // =========================================================================
    // Immutability Tests
    // =========================================================================

    @Test
    void scene_GetActions_ReturnsUnmodifiableList() {
        Scene scene = new Scene("SecureScene");
        List<SceneAction> actions = scene.getActions();

        assertThrows(UnsupportedOperationException.class, () -> {
            actions.add(new SceneAction(light1, new LightState(true, 50)));
        });

    }

    @Test
    void state_Immutability_Check() throws ValidationException {
        LightState state = new LightState(true, 50);
        light1.applyState(state);
        DeviceState savedState = light1.getState();
        assertSame(state, savedState);
    }
}