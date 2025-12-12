import devices.*;
import hub.*;
import users.*;
import users.roles.RoleFactory;

import java.util.Map;

/**
 * responsible for loading initial data into the smart home system.
 */
public class SmartHomeBootstrap {

    public void loadInitialData(SmartHomeHub hub, Map<String, User> userDatabase) {
        System.out.println("Loading system data...");

        try {
            // intialize users
            User admin = new User("Thomas", RoleFactory.createAdminRole());
            User parent = new User("Zijian", RoleFactory.createParentRole());
            User child = new User("Charlie", RoleFactory.createChildRole());

            userDatabase.put(admin.getUsername(), admin);
            userDatabase.put(parent.getUsername(), parent);
            userDatabase.put(child.getUsername(), child);

            // register devices
            SmartLight light1 = new SmartLight("LivingRoomLight1");
            SmartLight light2 = new SmartLight("LivingRoomLight2");
            SmartThermostat thermo = new SmartThermostat("DownstairsThermostat");
            SmartLock lock = new SmartLock("FrontDoorLock");

            hub.registerDevice(admin, light1);
            hub.registerDevice(admin, light2);
            hub.registerDevice(admin, thermo);
            hub.registerDevice(admin, lock);

            // create default scene
            Scene movieNight = new Scene("Movie Night");
            movieNight.addAction(new SceneAction(light1, new LightState(true, 20)));
            movieNight.addAction(new SceneAction(light2, new LightState(true, 20)));
            movieNight.addAction(new SceneAction(lock, new LockState(true)));
            movieNight.addAction(new SceneAction(thermo, new ThermostatState(true, 22.0)));

            hub.createScene(admin, movieNight);

            System.out.println("System data loaded successfully.");

        } catch (Exception e) {
            System.err.println("Bootstrap Error: " + e.getMessage());
        }
    }
}