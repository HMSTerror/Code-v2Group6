package devices;

public class ThermostatState implements DeviceState {
    private final boolean power;
    private final double targetTemperature;

    public ThermostatState(boolean power, double targetTemperature) {
        if (targetTemperature < 5.0 || targetTemperature > 30.0) {
            throw new IllegalArgumentException("Temperature must be 5.0-30.0");
        }
        this.power = power;
        this.targetTemperature = targetTemperature;
    }

    public boolean isPowerOn() { return power; }
    public double getTargetTemperature() { return targetTemperature; }

    @Override
    public String toString() {
        return "ThermostatState{power=" + power + ", temp=" + targetTemperature + "}";
    }
}