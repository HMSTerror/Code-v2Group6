package devices;

import exceptions.ValidationException;

/**
 * SmartThermostat:
 *  - power: Boolean
 *  - targetTemperature: Double
 */
public class SmartThermostat extends Device {
    public SmartThermostat(String name) {
        super(name, "THERMOSTAT");
        this.state = new ThermostatState(false, 20.0);
    }

    @Override
    public void applyState(DeviceState targetState) throws ValidationException {
        if (targetState instanceof ThermostatState ts) {
            this.state = ts;
            System.out.println("-> [Thermostat] " + name + " set to " + ts);
        } else {
            throw new ValidationException("Invalid state type for SmartThermostat");
        }
    }
}