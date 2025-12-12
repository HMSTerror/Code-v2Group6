package hub;

import devices.Device;
import devices.DeviceState;
import exceptions.ExecutionException;
import users.Permission;
import users.User;

import java.util.LinkedHashMap;
import java.util.Map;

public class SceneExecutor {

    public void execute(Scene scene, User user) throws ExecutionException {
        if (!user.hasPermission(Permission.EXECUTE_SCENES)) {
            throw new SecurityException("User lacks EXECUTE_SCENES permission");
        }

        Map<Device, DeviceState> originalStates = new LinkedHashMap<>();

        // check permissions and save original states
        for (SceneAction action : scene.getActions()) {
            Device device = action.getDevice();
            if (!user.getRole().canControlDevice(device.getType())) {
                throw new ExecutionException("User cannot control device type: " + device.getType());
            }
            originalStates.put(device, device.getState());
        }

        // execute actions
        try {
            for (SceneAction action : scene.getActions()) {
                action.execute();
            }
            System.out.println("Scene '" + scene.getName() + "' executed successfully.");
        } catch (Exception e) {
            System.err.println("Scene execution failed (" + e.getMessage() + "). Rolling back...");
            rollback(originalStates);
            throw new ExecutionException("Scene failed and rolled back.", e);
        }
    }

    private void rollback(Map<Device, DeviceState> originalStates) {
        originalStates.forEach((device, state) -> {
            try {
                device.restoreState(state);
            } catch (Exception e) {
                System.err.println("Failed to rollback device: " + device.getName());
            }
        });
        System.out.println("Rollback complete.");
    }
}