import devices.*;
import hub.*;
import users.*;
import exceptions.*;

import java.util.*;

/**
 * main console application for smart home system.
 * intializes system via SmartHomeBootstrap and delegates scene creation to SceneCreationWizard.
 */
public class SmartHomeConsole {

    private final SmartHomeHub hub;
    private final Map<String, User> userDatabase = new HashMap<>();
    private User currentUser = null;
    private final Scanner scanner;

    // introduce wizard for scene creation
    private final SceneCreationWizard sceneWizard;

    public SmartHomeConsole() {
        this.hub = new SmartHomeHub();
        this.scanner = new Scanner(System.in);

        // load initial data
        new SmartHomeBootstrap().loadInitialData(hub, userDatabase);

        // initialize scene creation wizard
        this.sceneWizard = new SceneCreationWizard(hub, scanner);
    }

    public void start() {
        System.out.println("=== Smart Home System v2.0 (Refactored) ===");
        while (true) {
            if (currentUser == null) {
                handleLogin();
            } else {
                handleCommand();
            }
        }
    }

    private void handleLogin() {
        System.out.println("\nUsers: " + userDatabase.keySet());
        System.out.print("Login > ");
        String input = scanner.nextLine().trim();

        if (userDatabase.containsKey(input)) {
            currentUser = userDatabase.get(input);
            System.out.println("Welcome, " + currentUser.getUsername() + " [" + currentUser.getRole().getName() + "]");
        } else if (input.equalsIgnoreCase("exit")) {
            System.exit(0);
        } else {
            System.out.println("User not found.");
        }
    }

    private void handleCommand() {
        System.out.print(currentUser.getUsername() + "@Home > ");
        String line = scanner.nextLine().trim();
        if (line.isEmpty()) return;

        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        try {
            switch (cmd) {
                case "list":
                    hub.getAllDevices().forEach(d -> System.out.println(d));
                    break;
                case "set":
                    if (arg.isEmpty()) System.out.println("Usage: set <deviceName> <property> <value>");
                    else handleSetCommand(arg);
                    break;
                case "scene":
                    if (arg.isEmpty()) System.out.println("Usage: scene <SceneName>");
                    else hub.executeScene(currentUser, arg);
                    break;
                case "add_device":
                    if (arg.isEmpty()) System.out.println("Usage: add_device <type> <name>");
                    else handleAddDevice(arg);
                    break;
                case "create_scene":
                    if (arg.isEmpty()) System.out.println("Usage: create_scene <SceneName>");
                    else sceneWizard.start(currentUser, arg);
                    break;
                case "logout":
                    currentUser = null;
                    break;
                case "exit":
                    System.exit(0);
                    break;
                case "help":
                    printHelp();
                    break;
                default:
                    System.out.println("Unknown command.");
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  list                      - Show all devices");
        System.out.println("  set <dev> <prop> <val>    - Control a device");
        System.out.println("  scene <name>              - Execute a scene");
        System.out.println("  add_device <type> <name>  - Register new device");
        System.out.println("  create_scene <name>       - Launch scene wizard");
        System.out.println("  logout / exit");
    }


    private void handleAddDevice(String args) {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            System.out.println("Error: Usage: add_device <type> <name>");
            return;
        }

        String type = parts[0].toLowerCase();
        String name = parts[1];
        Device newDevice = null;

        switch (type) {
            case "light": newDevice = new SmartLight(name); break;
            case "thermostat": newDevice = new SmartThermostat(name); break;
            case "lock": newDevice = new SmartLock(name); break;
            default:
                System.out.println("Error: Unknown device type '" + type + "'.");
                return;
        }

        try {
            hub.registerDevice(currentUser, newDevice);
            System.out.println("Success: Registered new " + type + " named '" + name + "'.");
        } catch (SecurityException e) {
            System.out.println("ACCESS DENIED: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleSetCommand(String args) {
        String[] parts = args.split("\\s+");
        if (parts.length < 3) {
            System.out.println("Error: Invalid format.");
            return;
        }
        String deviceName = parts[0];
        String property = parts[1].toLowerCase();
        String value = parts[2];

        Device device = hub.getDevice(deviceName);
        if (device == null) {
            System.out.println("Error: Device not found.");
            return;
        }

        if (!currentUser.getRole().canControlDevice(device.getType())) {
            System.out.println("ACCESS DENIED: User cannot control " + device.getType());
            return;
        }

        try {
            if (device instanceof SmartLight) updateLight((SmartLight) device, property, value);
            else if (device instanceof SmartThermostat) updateThermostat((SmartThermostat) device, property, value);
            else if (device instanceof SmartLock) updateLock((SmartLock) device, property, value);
            else System.out.println("Error: Device type not supported for control.");

            System.out.println("Success: Set " + deviceName + " " + property + " to " + value);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // --- Set Helper Methods ---
    private void updateLight(SmartLight light, String prop, String val) throws ValidationException {
        LightState cur = (LightState) light.getState();
        boolean p = cur.isPowerOn();
        int b = cur.getBrightness();
        if (prop.equals("power")) p = Boolean.parseBoolean(val);
        else if (prop.equals("brightness")) { b = Integer.parseInt(val); if(b>0) p=true; }
        else throw new IllegalArgumentException("Unknown property");
        light.applyState(new LightState(p, b));
    }

    private void updateThermostat(SmartThermostat t, String prop, String val) throws ValidationException {
        ThermostatState cur = (ThermostatState) t.getState();
        boolean p = cur.isPowerOn();
        double temp = cur.getTargetTemperature();
        if (prop.equals("power")) p = Boolean.parseBoolean(val);
        else if (prop.startsWith("temp")) { temp = Double.parseDouble(val); p=true; }
        else throw new IllegalArgumentException("Unknown property");
        t.applyState(new ThermostatState(p, temp));
    }

    private void updateLock(SmartLock l, String prop, String val) throws ValidationException {
        if (prop.equals("locked")) l.applyState(new LockState(Boolean.parseBoolean(val)));
        else throw new IllegalArgumentException("Unknown property");
    }
}