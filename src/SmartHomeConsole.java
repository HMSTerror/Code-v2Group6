import devices.*;
import hub.*;
import users.*;
import users.roles.RoleFactory;
import exceptions.*;

import java.util.*;

public class SmartHomeConsole {

    private final SmartHomeHub hub;
    private final Map<String, User> userDatabase = new HashMap<>();
    private User currentUser = null;
    private final Scanner scanner;

    public SmartHomeConsole() {
        this.hub = new SmartHomeHub();
        this.scanner = new Scanner(System.in);
        initializeSystem();
    }

    private void initializeSystem() {
        // create users
        User admin = new User("Thomas", RoleFactory.createAdminRole());
        User parent = new User("Zijian", RoleFactory.createParentRole());
        User child = new User("Charlie", RoleFactory.createChildRole());

        userDatabase.put(admin.getUsername(), admin);
        userDatabase.put(parent.getUsername(), parent);
        userDatabase.put(child.getUsername(), child);

        // register devices
        try {
            SmartLight light1 = new SmartLight("LivingRoomLight1");
            SmartLight light2 = new SmartLight("LivingRoomLight2");
            SmartThermostat thermo = new SmartThermostat("DownstairsThermostat");
            SmartLock lock = new SmartLock("FrontDoorLock");

            hub.registerDevice(admin, light1);
            hub.registerDevice(admin, light2);
            hub.registerDevice(admin, thermo);
            hub.registerDevice(admin, lock);

            // create scenes
            Scene movieNight = new Scene("Movie Night");
            movieNight.addAction(new SceneAction(light1, new LightState(true, 20)));
            movieNight.addAction(new SceneAction(light2, new LightState(true, 20)));
            movieNight.addAction(new SceneAction(lock, new LockState(true)));
            movieNight.addAction(new SceneAction(thermo, new ThermostatState(true, 22.0)));

            hub.createScene(admin, movieNight);

        } catch (Exception e) {
            System.err.println("Initialization Error: " + e.getMessage());
        }
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
                case "logout":
                    currentUser = null;
                    break;
                case "exit":
                    System.exit(0);
                    break;
                case "help":
                    System.out.println("Commands: list, set <deviceName> <property> <value>, scene <name>, add_device <type> <name>, create_scene <SceneName>, logout, exit");
                    break;
                case "add_device":
                    if (arg.isEmpty()) System.out.println("Usage: add_device <type> <name>");
                    else handleAddDevice(arg);
                    break;

                case "create_scene":
                    if (arg.isEmpty()) System.out.println("Usage: create_scene <SceneName>");
                    else handleCreateScene(arg);
                    break;
                default:
                    System.out.println("Unknown command.");
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
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

        // 根据类型工厂化创建
        switch (type) {
            case "light":
                newDevice = new SmartLight(name);
                break;
            case "thermostat":
                newDevice = new SmartThermostat(name);
                break;
            case "lock":
                newDevice = new SmartLock(name);
                break;
            default:
                System.out.println("Error: Unknown device type '" + type + "'. Supported: light, thermostat, lock.");
                return;
        }

        try {
            // use hub to register
            hub.registerDevice(currentUser, newDevice);
            System.out.println("Success: Registered new " + type + " named '" + name + "'.");
        } catch (SecurityException e) {
            System.out.println("ACCESS DENIED: You do not have permission to register devices.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleCreateScene(String sceneName) {
        if (!currentUser.getRole().hasPermission(Permission.EDIT_SCENES)) {
            System.out.println("ACCESS DENIED: You do not have permission to create scenes.");
            return;
        }

        Scene newScene = new Scene(sceneName);
        System.out.println("--- Creating Scene: " + sceneName + " ---");
        System.out.println("(Type 'done' to finish scene, 'cancel' to abort)");

        // --- 外层循环：选择设备 ---
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

            // 临时存储用户为该设备设置的所有属性 (key=property, value=value)
            Map<String, String> deviceInputs = new HashMap<>();
            System.out.println("  [Configuring " + devName + " (" + device.getType() + ")]");
            System.out.println("  (Type 'next' to confirm device, 'back' to discard this device)");

            // --- 内层循环：设置该设备的多个属性 ---
            boolean deviceConfigured = false;
            while (true) {
                System.out.print("    > Enter property (power/brightness/temp/locked) or 'next': ");
                String prop = scanner.nextLine().trim().toLowerCase();

                if (prop.equals("next")) {
                    deviceConfigured = true;
                    break;
                }
                if (prop.equals("back")) {
                    System.out.println("    Discarded settings for " + devName);
                    break; // 跳出内层循环，不保存
                }

                // 简单的属性名检查（提升体验，防止输错）
                if (!isValidProperty(device, prop)) {
                    System.out.println("    Invalid property for this device type.");
                    continue;
                }

                System.out.print("    > Enter value for " + prop + ": ");
                String val = scanner.nextLine().trim();

                // 立即进行格式校验，如果非法直接报错，不存入 map
                try {
                    validateInputFormat(prop, val); // 校验格式
                    deviceInputs.put(prop, val);    // 存入暂存区
                    System.out.println("      (Pending) " + prop + " = " + val);
                } catch (Exception e) {
                    System.out.println("      Error: " + e.getMessage() + ". Please try again.");
                }
            }

            // --- 如果用户确认了 (next)，则生成动作 ---
            if (deviceConfigured && !deviceInputs.isEmpty()) {
                try {
                    // 将所有暂存的属性合并，创建一个完整的 State 对象
                    DeviceState finalState = createStateFromInputs(device, deviceInputs);
                    newScene.addAction(new SceneAction(device, finalState));
                    System.out.println("  -> Action added for " + devName);
                } catch (Exception e) {
                    System.out.println("  Error creating action: " + e.getMessage());
                }
            } else if (deviceConfigured) {
                System.out.println("  No properties set for " + devName + ", skipping.");
            }
        }

        // 保存场景
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

    // --- 辅助方法 1: 检查属性名是否合法 ---
    private boolean isValidProperty(Device d, String prop) {
        if (d instanceof SmartLight) return prop.equals("power") || prop.equals("brightness");
        if (d instanceof SmartThermostat) return prop.equals("power") || prop.equals("temp") || prop.equals("temperature");
        if (d instanceof SmartLock) return prop.equals("locked");
        return false;
    }

    // --- 辅助方法 2: 严格校验输入格式 (不生成对象，只查格式) ---
    private void validateInputFormat(String prop, String val) {
        if (prop.equals("power") || prop.equals("locked")) {
            // 严格布尔校验
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

    // --- 辅助方法 3: 从 Map 构建强类型 State ---
    private DeviceState createStateFromInputs(Device device, Map<String, String> inputs) {
        if (device instanceof SmartLight) {
            // 默认值逻辑
            boolean power = false;
            int brightness = 0;

            // 1. 先尝试读取 power
            if (inputs.containsKey("power")) {
                power = Boolean.parseBoolean(inputs.get("power"));
            }
            // 2. 读取 brightness
            if (inputs.containsKey("brightness")) {
                brightness = Integer.parseInt(inputs.get("brightness"));
                // 智能逻辑：如果用户设置了亮度>0，且没显式设置关机，则隐含开机
                if (brightness > 0 && !inputs.containsKey("power")) {
                    power = true;
                }
            }
            return new LightState(power, brightness);
        }
        else if (device instanceof SmartThermostat) {
            boolean power = false;
            double temp = 20.0; // 默认

            if (inputs.containsKey("power")) {
                power = Boolean.parseBoolean(inputs.get("power"));
            }
            if (inputs.containsKey("temp")) {
                temp = Double.parseDouble(inputs.get("temp"));
                // 智能逻辑：设置温度隐含开机
                if (!inputs.containsKey("power")) power = true;
            }
            if (inputs.containsKey("temperature")) { // 兼容全称
                temp = Double.parseDouble(inputs.get("temperature"));
                if (!inputs.containsKey("power")) power = true;
            }
            return new ThermostatState(power, temp);
        }
        else if (device instanceof SmartLock) {
            boolean locked = true;
            if (inputs.containsKey("locked")) {
                locked = Boolean.parseBoolean(inputs.get("locked"));
            }
            return new LockState(locked);
        }
        throw new IllegalArgumentException("Unknown device type");
    }


    private void handleSetCommand(String args) {
        String[] parts = args.split("\\s+");
        if (parts.length < 3) {
            System.out.println("Error: Invalid format. Usage: set <DeviceName> <Property> <Value>");
            return;
        }

        String deviceName = parts[0];
        String property = parts[1].toLowerCase();
        String value = parts[2];

        // find device
        Device device = hub.getDevice(deviceName);
        if (device == null) {
            System.out.println("Error: Device '" + deviceName + "' not found.");
            return;
        }

        // prevent unauthorized access
        if (!currentUser.getRole().canControlDevice(device.getType())) {
            System.out.println("ACCESS DENIED: User " + currentUser.getUsername() +
                    " is not allowed to control " + device.getType() + "s.");
            return;
        }

        // update device state based on type and property
        try {
            if (device instanceof SmartLight) {
                updateLight((SmartLight) device, property, value);
            } else if (device instanceof SmartThermostat) {
                updateThermostat((SmartThermostat) device, property, value);
            } else if (device instanceof SmartLock) {
                updateLock((SmartLock) device, property, value);
            } else {
                System.out.println("Error: Control logic for device type " + device.getType() + " not implemented.");
            }
            System.out.println("Success: " + deviceName + " " + property + " set to " + value);

        } catch (NumberFormatException e) {
            System.out.println("Error: Invalid number format for value '" + value + "'");
        } catch (IllegalArgumentException | ValidationException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: Unexpected error - " + e.getMessage());
        }
    }

    // updateLight
    private void updateLight(SmartLight light, String property, String value) throws ValidationException {
        // get current state
        LightState current = (LightState) light.getState();
        boolean newPower = current.isPowerOn();
        int newBrightness = current.getBrightness();

        switch (property) {
            case "power":
                newPower = Boolean.parseBoolean(value);
                break;
            case "brightness":
                newBrightness = Integer.parseInt(value);
                // if brightness > 0, ensure power is on
                if (newBrightness > 0) newPower = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown property for Light: " + property);
        }
        // apply new state
        light.applyState(new LightState(newPower, newBrightness));
    }

    // updateThermostat
    private void updateThermostat(SmartThermostat thermo, String property, String value) throws ValidationException {
        ThermostatState current = (ThermostatState) thermo.getState();
        boolean newPower = current.isPowerOn();
        double newTemp = current.getTargetTemperature();

        switch (property) {
            case "power":
                newPower = Boolean.parseBoolean(value);
                break;
            case "temp":
            case "temperature":
                newTemp = Double.parseDouble(value);
                newPower = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown property for Thermostat: " + property);
        }
        thermo.applyState(new ThermostatState(newPower, newTemp));
    }

    // updateLock
    private void updateLock(SmartLock lock, String property, String value) throws ValidationException {
        switch (property) {
            case "locked":
                boolean newLocked = Boolean.parseBoolean(value);
                lock.applyState(new LockState(newLocked));
                break;
            default:
                throw new IllegalArgumentException("Unknown property for Lock: " + property);
        }
    }
}