package devices;

import exceptions.ValidationException;

/**
 * SmartLight:
 *  - power: Boolean (true=ON, false=OFF)
 *  - brightness: Integer (0-100)
 */
public class SmartLight extends Device {
    public SmartLight(String name) {
        super(name, "LIGHT");
        this.state = new LightState(false, 100);
    }

    @Override
    public void applyState(DeviceState targetState) throws ValidationException {
        if (targetState instanceof LightState ls) {
            this.state = ls;
            System.out.println("-> [Light] " + name + " set to " + ls);
        } else {
            throw new ValidationException("Invalid state type for SmartLight");
        }
    }
}