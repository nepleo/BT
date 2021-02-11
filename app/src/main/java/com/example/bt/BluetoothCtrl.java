package com.example.bt;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * 蓝牙的控制类
 * */
public class BluetoothCtrl {
    /**常量:蓝牙配对绑定过滤监听器名称*/
    static public final String PAIRING_REQUEST = "android.bluetooth.device.action.PAIRING_REQUEST";
    /**
     * 对蓝牙设备进行配对
     * /Settings/src/com/android/settings/bluetooth/CachedBluetoothDevice.java
     */
    static public boolean createBond(BluetoothDevice btDevice)
            throws Exception
    {
        Class<? extends BluetoothDevice> btClass = btDevice.getClass();
        Method createBondMethod = btClass.getMethod("createBond");
        Boolean returnValue = (Boolean) createBondMethod.invoke(btDevice);
        return returnValue.booleanValue();
    }

    /**
     * 解除蓝牙设备的配对
     * /Settings/src/com/android/settings/bluetooth/CachedBluetoothDevice.java
     */
    static public boolean removeBond(BluetoothDevice btDevice)
            throws Exception
    {
        Class<? extends BluetoothDevice> btClass = btDevice.getClass();
        Method removeBondMethod = btClass.getMethod("removeBond");
        Boolean returnValue = (Boolean) removeBondMethod.invoke(btDevice);
        return returnValue.booleanValue();
    }

    /**
     * 设定配对密码
     * /Settings/src/com/android/settings/bluetooth/CachedBluetoothDevice.java
     */
    static public boolean setPin(BluetoothDevice btDevice, String str)
            throws Exception
    {
        Boolean returnValue = false;
        try{
            Class<? extends BluetoothDevice> btClass = btDevice.getClass();
            Method removeBondMethod = btClass.getDeclaredMethod("setPin", new Class[] { byte[].class });
//        	byte[] ar = new byte[]{0x31,0x32,0x33,0x34};
//        	Method removeBondMethod =
//        		btClass.getMethod("setPin",new Class[]{Array.newInstance(byte.class,4).getClass()});
//            Boolean returnValue = (Boolean) removeBondMethod.invoke(btDevice, ar);
            returnValue = (Boolean) removeBondMethod.invoke(btDevice, new Object[] { str.getBytes() });
            Log.d("returnValue", ">>setPin:" + returnValue.toString());
        }catch (SecurityException e){
            // throw new RuntimeException(e.getMessage());
            Log.e("returnValue", ">>setPin:" + e.getMessage());
            e.printStackTrace();
        }catch (IllegalArgumentException e){
            // throw new RuntimeException(e.getMessage());
            Log.e("returnValue", ">>setPin:" + e.getMessage());
            e.printStackTrace();
        }catch (Exception e){
            Log.e("returnValue", ">>setPin:" + e.getMessage());
            e.printStackTrace();
        }
        return returnValue.booleanValue();
    }

    /**
     * cancel Pairing User Input
     * @see android.permission.WRITE_SECURE_SETTINGS<br/>
     *      Permission is only granted to system apps
     * */
    static public boolean cancelPairingUserInput(BluetoothDevice btDevice)
            throws Exception
    {
        Class<? extends BluetoothDevice> btClass = btDevice.getClass();
        Method createBondMethod = btClass.getMethod("cancelPairingUserInput");
        // cancelBondProcess()
        Boolean returnValue = (Boolean) createBondMethod.invoke(btDevice);
        return returnValue.booleanValue();
    }

    /**
     * cancel Bond Process
     * @see android.permission.WRITE_SETTINGS
     * */
    static public boolean cancelBondProcess(BluetoothDevice btDevice)
            throws Exception
    {
        Boolean returnValue = false;
        try
        {
            Class<? extends BluetoothDevice> btClass = btDevice.getClass();
            Method createBondMethod = btClass.getMethod("cancelBondProcess");
            returnValue = (Boolean) createBondMethod.invoke(btDevice);
        }
        catch (SecurityException e)
        {
            // throw new RuntimeException(e.getMessage());
            Log.e("returnValue", ">>cancelBondProcess:" + e.getMessage());
            e.printStackTrace();
        }
        catch (IllegalArgumentException e)
        {
            // throw new RuntimeException(e.getMessage());
            Log.e("returnValue", ">>cancelBondProcess:" + e.getMessage());
            e.printStackTrace();
        }
        catch (Exception e)
        {
            Log.e("returnValue", ">>cancelBondProcess:" + e.getMessage());
            e.printStackTrace();
        }
        return returnValue.booleanValue();
    }
}

