import devices.*;
import hub.*;
import users.Permission;
import users.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * interactive wizard for creating or editing scenes.
 * Handles multi-step input, validation, and scene assembly.
 */
public class SceneCreationWizard {

    private final SmartHomeHub hub;
    private final Scanner scanner;

    public SceneCreationWizard(SmartHomeHub hub, Scanner scanner) {
        this.hub = hub;
        this.scanner = scanner;
    }

    public void start(User currentUser, String sceneName) {
        // check permissions
        if (!currentUser.hasPermission(Permission.EDIT_SCENES)) {
            System.out.println("ACCESS DENIED: You do not have permission to create scenes.");
            return;
        }

        Scene newScene = new Scene(sceneName);
        System.out.println("--- Creating Scene: " + sceneName + " ---");
        System.out.println("(Type 'done' to finish scene, 'cancel' to abort)");

        // choose devices
        while (true) {
            System.out.print("\n> Enter device name (or 'done'): ");
            String devName = scanner.nextLine().trim();

            if (devName.equalsIgnoreCase("done")) break;
            if (devName.equalsIgnoreCase("cancel")) {
                System.out.println("Scene creation aborted.");
                return;
            }

            Device device = hub.getDevice(devName);
            if (device == null) {
                System.out.println("  Error: Device not found.");
                continue;
            }

            // execute device configuration
            configureDeviceForScene(newScene, device);
        }

        if (newScene.getActions().isEmpty()) {
            System.out.println("Scene is empty. Not saved.");
        } else {
            try {
                hub.createScene(currentUser, newScene);
                System.out.println("\nSuccess: Scene '" + sceneName + "' created/updated.");
            } catch (Exception e) {
                System.out.println("Failed to save scene: " + e.getMessage());
            }
        }
    }

    private void configureDeviceForScene(Scene scene, Device device) {
        Map<String, String> deviceInputs = new HashMap<>();
        System.out.println("  [Configuring " + device.getName() + " (" + device.getType() + ")]");
        System.out.println("  (Type 'next' to confirm device, 'back' to discard this device)");

        // set properties loop
        while (true) {
            System.out.print("    > Enter property (power/brightness/temp/locked) or 'next': ");
            String prop = scanner.nextLine().trim().toLowerCase();

            if (prop.equals("next")) break;
            if (prop.equals("back")) {
                System.out.println("    Discarded settings for " + device.getName());
                return;
            }

            if (!isValidProperty(device, prop)) {
                System.out.println("    Invalid property for this device type.");
                continue;
            }

            System.out.print("    > Enter value for " + prop + ": ");
            String val = scanner.nextLine().trim();

            try {
                validateInputFormat(prop, val);
                deviceInputs.put(prop, val);
                System.out.println("      (Pending) " + prop + " = " + val);
            } catch (Exception e) {
                System.out.println("      Error: " + e.getMessage() + ". Please try again.");
            }
        }

        // create action if inputs exist
        if (!deviceInputs.isEmpty()) {
            try {
                DeviceState finalState = createStateFromInputs(device, deviceInputs);
                scene.addAction(new SceneAction(device, finalState));
                System.out.println("  -> Action added for " + device.getName());
            } catch (Exception e) {
                System.out.println("  Error creating action: " + e.getMessage());
            }
        } else {
            System.out.println("  No properties set for " + device.getName() + ", skipping.");
        }
    }


    private boolean isValidProperty(Device d, String prop) {
        if (d instanceof SmartLight) return prop.equals("power") || prop.equals("brightness");
        if (d instanceof SmartThermostat) return prop.equals("power") || prop.equals("temp") || prop.equals("temperature");
        if (d instanceof SmartLock) return prop.equals("locked");
        return false;
    }

    private void validateInputFormat(String prop, String val) {
        if (prop.equals("power") || prop.equals("locked")) {
            if (!val.equalsIgnoreCase("true") && !val.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Value must be 'true' or 'false'");
            }
        } else if (prop.equals("brightness")) {
            try {
                int b = Integer.parseInt(val);
                if (b < 0 || b > 100) throw new IllegalArgumentException("Brightness must be 0-100");
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Brightness must be an integer");
            }
        } else if (prop.startsWith("temp")) {
            try {
                Double.parseDouble(val);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Temperature must be a number");
            }
        }
    }

    private DeviceState createStateFromInputs(Device device, Map<String, String> inputs) {
        if (device instanceof SmartLight) {
            boolean power = false;
            int brightness = 0;
            if (inputs.containsKey("power")) power = Boolean.parseBoolean(inputs.get("power"));
            if (inputs.containsKey("brightness")) {
                brightness = Integer.parseInt(inputs.get("brightness"));
                if (brightness > 0 && !inputs.containsKey("power")) power = true;
            }
            return new LightState(power, brightness);
        } else if (device instanceof SmartThermostat) {
            boolean power = false;
            double temp = 20.0;
            if (inputs.containsKey("power")) power = Boolean.parseBoolean(inputs.get("power"));
            if (inputs.containsKey("temp") || inputs.containsKey("temperature")) {
                String tVal = inputs.getOrDefault("temp", inputs.get("temperature"));
                temp = Double.parseDouble(tVal);
                if (!inputs.containsKey("power")) power = true;
            }
            return new ThermostatState(power, temp);
        } else if (device instanceof SmartLock) {
            boolean locked = true;
            if (inputs.containsKey("locked")) locked = Boolean.parseBoolean(inputs.get("locked"));
            return new LockState(locked);
        }
        throw new IllegalArgumentException("Unknown device type");
    }
}