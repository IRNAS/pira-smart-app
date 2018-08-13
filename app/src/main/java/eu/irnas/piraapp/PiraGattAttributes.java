/*
 * pira-smart-app
 *
 * Copyright (C) 2018 vid553, IRNAS <www.irnas.eu>
 */
package eu.irnas.piraapp;

import java.util.HashMap;
import java.util.UUID;

public class PiraGattAttributes {
    static final UUID PIRA_SERVICE_UUID = UUID.fromString("0000b000-0000-1000-8000-00805f9b34fb");
    static final UUID PIRA_SET_TIME_CHARACTERISTIC_UUID =    UUID.fromString("0000b001-0000-1000-8000-00805f9b34fb");
    static final UUID PIRA_GET_TIME_CHARACTERISTIC_UUID =    UUID.fromString("0000b002-0000-1000-8000-00805f9b34fb");
    static final UUID PIRA_STATUS_CHARACTERISTIC_UUID =      UUID.fromString("0000b003-0000-1000-8000-00805f9b34fb");
    static final UUID PIRA_ON_PERIOD_CHARACTERISTIC_UUID =   UUID.fromString("0000b004-0000-1000-8000-00805f9b34fb");
    static final UUID PIRA_OFF_PERIOD_CHARACTERISTIC_UUID =  UUID.fromString("0000b005-0000-1000-8000-00805f9b34fb");
    static final UUID PIRA_BATTERY_LEVEL_CHARACTERISTIC_UUID=UUID.fromString("0000b006-0000-1000-8000-00805f9b34fb");

    private static HashMap<UUID,String> attributes = new HashMap();

    static {
        attributes.put(PIRA_SERVICE_UUID, "Pira Service");
        attributes.put(PIRA_SET_TIME_CHARACTERISTIC_UUID,"Set Time");
        attributes.put(PIRA_GET_TIME_CHARACTERISTIC_UUID,"Get Time");
        attributes.put(PIRA_STATUS_CHARACTERISTIC_UUID,"Get Status");
        attributes.put(PIRA_ON_PERIOD_CHARACTERISTIC_UUID,"Set OnPeriod");
        attributes.put(PIRA_OFF_PERIOD_CHARACTERISTIC_UUID,"Set OffPeriod");
        attributes.put(PIRA_BATTERY_LEVEL_CHARACTERISTIC_UUID, "Get Battery");
    }

    public static String lookup (String uuid) {
        return attributes.get(uuid);
    }

}
