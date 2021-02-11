package com.example.bt;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;
import java.util.regex.Pattern;

public class ScanActivity extends AppCompatActivity {

    private BluetoothAdapter mbluetoothAdapter;
    public final static String EXTRA_DEVICE_TYPE = "android.bluetooth.device.extra.DEVICE_TYPE";

    /**日志提示标签*/
    private String TAG = "Log Warning :";

    private final int REQUEST_BT_ENABLE = 200;

    private ListView mlvList = null;

     /**Discovery is Finished */
    private boolean _discoveryFinished;

    /**CONST: device type bltetooth 2.1*/
    public static final int DEVICE_TYPE_BREDR = 0x01;
    /**CONST: device type bltetooth 4.0 ble*/
    public static final int DEVICE_TYPE_BLE = 0x02;
    /**CONST: device type bltetooth double mode*/
    public static final int DEVICE_TYPE_DUMO = 0x03;


    /**
     * Storage the found bluetooth devices
     * format:<MAC, <Key,Val>>;Key=[RSSI/NAME/COD(class od device)/BOND/UUID]
     * */
    private Hashtable<String, Hashtable<String, String>> mhtFDS = null;

    /**ListView的动态数组对象(存储用于显示的列表数组)*/
    private ArrayList<HashMap<String, Object>> malListItem = null;
    /**SimpleAdapter对象(列表显示容器对象)*/
    private SimpleAdapter msaListItemAdapter = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        //判断版本号
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                String[] permissions = new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
                ActivityCompat.requestPermissions(this, permissions, 10);
                return;
            }
        }

        //在创建设备时，检查手机设备是否支持蓝牙和ble,如果不支持就返回上一层并提示不支持
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.i(TAG, "不支持BLE设备");
            Toast.makeText(this, R.string.ble_not_support, Toast.LENGTH_SHORT).show();
            finish();
        }

        //初始化蓝牙设备
        if (mbluetoothAdapter == null) {
            mbluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (mbluetoothAdapter == null) {
            Log.i(TAG, "没有获取蓝牙设备");
            Toast.makeText(this, R.string.bt_not_get_device, Toast.LENGTH_SHORT).show();
            finish();
        }
        //请求打开蓝牙权限
        if (!mbluetoothAdapter.enable()) {
            Intent btEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(btEnable, REQUEST_BT_ENABLE);
        }

        this.mlvList = (ListView)this.findViewById(R.id.list_view);

        /* 选择项目后返回给调用页面 */
        this.mlvList.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3){
                String sMAC = ((TextView)arg1.findViewById(R.id.device_item_ble_mac)).getText().toString();
                Intent result = new Intent();
                result.setClass(ScanActivity.this, LinkActivity.class);
                result.putExtra("MAC", sMAC);
                result.putExtra("RSSI", mhtFDS.get(sMAC).get("RSSI"));
                result.putExtra("NAME", mhtFDS.get(sMAC).get("NAME"));
                result.putExtra("COD", mhtFDS.get(sMAC).get("COD"));
                result.putExtra("BOND", mhtFDS.get(sMAC).get("BOND"));
                result.putExtra("DEVICE_TYPE", toDeviceTypeString(mhtFDS.get(sMAC).get("DEVICE_TYPE")));
                startActivity(result);
            }
        });
        //立即启动扫描线程
        new scanDeviceTask().execute("");
    }

    /**扫描设备*/
    public void scanDevices(View view) {
        ensureDiscoverable();
        new scanDeviceTask().execute("");

    }

    //确保设备可被发现
    public void ensureDiscoverable() {
        if (mbluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //设置可被发现300s
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION , 300);
            startActivity(discoverableIntent);
        }
    }

    //已经配对的设备
    public void pairedDevice() {
        Set<BluetoothDevice> pairedDevice = mbluetoothAdapter.getBondedDevices();
        if (pairedDevice.size() > 0) {
            for (BluetoothDevice device : pairedDevice) {
                Log.i(TAG, "device name:" + device.getName() + "\n" + "device address :" + device.getAddress());
            }
        } else {
                Log.i(TAG, "没有找到设备");
        }
    }

    /**
     * 先注册一个广播来获取搜索结果,搜索到一个设备接受到一个广播
     * */
    private BroadcastReceiver _foundReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //蓝牙设备配置信息
            Hashtable<String, String> htDeviceInfo = new Hashtable<String, String>();
            Log.d(getString(R.string.app_name), ">>Scan for BT device");
            //获得搜索到的设备
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            //获得设备信息
            Bundle b = intent.getExtras();
            htDeviceInfo.put("RSSI", String.valueOf(b.get(BluetoothDevice.EXTRA_RSSI)));

            if (device.getName() == null)
                htDeviceInfo.put("NAME", "null");
            else
                htDeviceInfo.put("NAME", device.getName());

            htDeviceInfo.put("COD",  String.valueOf(b.get(BluetoothDevice.EXTRA_CLASS)));
            if (device.getBondState() == BluetoothDevice.BOND_BONDED)
                htDeviceInfo.put("BOND", getString(R.string.Scan_bond_bonded));
            else
                htDeviceInfo.put("BOND", getString(R.string.Scan_bond_nothing));

            String sDeviceType = String.valueOf(b.get(EXTRA_DEVICE_TYPE));

            if (!sDeviceType.equals("null"))
                htDeviceInfo.put("DEVICE_TYPE", sDeviceType);
            else
                htDeviceInfo.put("DEVICE_TYPE", "-1"); //不存在设备号

            mhtFDS.put(device.getAddress(), htDeviceInfo);

            showDevices();
         }

    };

    /**
     * Bluetooth scanning is finished processing.(broadcast listener)
     */
    private BroadcastReceiver _finshedReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent){
            Log.d(getString(R.string.app_name), ">>Bluetooth scanning is finished");
            _discoveryFinished = true; //set scan is finished
            unregisterReceiver(_foundReceiver);
            unregisterReceiver(_finshedReceiver);

            /* 提示用户选择需要连接的蓝牙设备 */
            if (null != mhtFDS && mhtFDS.size()>0){	//找到蓝牙设备
                Toast.makeText(ScanActivity.this,
                        getString(R.string.ScanActivity_msg_select_device),
                        Toast.LENGTH_SHORT).show();
            }else{	//未找到蓝牙设备
                Toast.makeText(ScanActivity.this,
                        getString(R.string.ScanActivity_msg_not_find_device),
                        Toast.LENGTH_LONG).show();
            }
        }
    };

    /**
    * show devices list
    * */
    protected void showDevices() {

        if (this.malListItem == null)  //当数组容器不存在时
            this.malListItem = new ArrayList<HashMap<String, Object>>();

        //如果列表适配器未创建则创建
        if (null == this.msaListItemAdapter) {
            //生成适配器的Item和动态数组对应的元素
            this.msaListItemAdapter = new SimpleAdapter(this, malListItem,
                    R.layout.list_view_item_devices,//ListItem的XML实现
                    //动态数组与ImageItem对应的子项
                    new String[] {"NAME","MAC", "COD", "RSSI", "DEVICE_TYPE", "BOND"},
                    //ImageItem的XML文件里面的一个ImageView,两个TextView ID
                    new int[] {R.id.device_item_ble_name,
                            R.id.device_item_ble_mac,
                            R.id.device_item_ble_cod,
                            R.id.device_item_ble_rssi,
                            R.id.device_item_ble_device_type,
                            R.id.device_item_ble_bond
                    }
            );
            //添加并且显示
            this.mlvList.setAdapter(this.msaListItemAdapter);
        }

        //构造适配器的数据
        this.malListItem.clear();//清除历史项
        Enumeration<String> e = this.mhtFDS.keys();
        //重新构造数据
        while (e.hasMoreElements()){
            HashMap<String, Object> map = new HashMap<String, Object>();
            String sKey = e.nextElement();
            map.put("MAC", sKey);
            map.put("NAME", this.mhtFDS.get(sKey).get("NAME"));
            map.put("RSSI", this.mhtFDS.get(sKey).get("RSSI"));
            map.put("COD", this.mhtFDS.get(sKey).get("COD"));
            map.put("BOND", this.mhtFDS.get(sKey).get("BOND"));
            map.put("DEVICE_TYPE", toDeviceTypeString(this.mhtFDS.get(sKey).get("DEVICE_TYPE")));
            this.malListItem.add(map);
        }
        this.msaListItemAdapter.notifyDataSetChanged(); //通知适配器内容发生变化自动跟新
        }

    /**
     * 将设备类型ID，转换成设备解释字符串
     * @return String
     * */
    private String toDeviceTypeString(String sDeviceTypeId){
        Pattern pt = Pattern.compile("^[-\\+]?[\\d]+$");
        if (pt.matcher(sDeviceTypeId).matches()){
            switch(Integer.valueOf(sDeviceTypeId)){
                case DEVICE_TYPE_BREDR:
                    return getString(R.string.device_type_bredr);
                case DEVICE_TYPE_DUMO:
                    return getString(R.string.device_type_dumo);
                case DEVICE_TYPE_BLE:
                    return getString(R.string.device_type_ble);
                default: //默认为蓝牙
                    return getString(R.string.device_type_bredr);
            }
        }
        else
            return sDeviceTypeId; //如果不是数字，则直接输出
    }


    /**
     * 开始扫描周围的蓝牙设备
     *  备注:进入这步前必须保证蓝牙设备已经被启动
     *
     * */
    private void startSearch(){
        _discoveryFinished = false; //标记搜索未结束

        //如果找到的设别对象为空，则创建这个对象。
        if (null == mhtFDS)
            this.mhtFDS = new Hashtable<String, Hashtable<String, String>>();
        else
            this.mhtFDS.clear();

        /**Register Receiver*/
        IntentFilter discoveryFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(_finshedReceiver, discoveryFilter);

        IntentFilter foundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(_foundReceiver, foundFilter);

        mbluetoothAdapter.startDiscovery();//start scan

        this.showDevices(); //the first scan clear show list
    }

    /*多线程处理:设备扫描监管线程*/
    private class scanDeviceTask extends AsyncTask<String, String, Integer>{
        /**常量:蓝牙未开启*/
        private static final int RET_BLUETOOTH_NOT_START = 0x0001;
        /**常量:设备搜索完成*/
        private static final int RET_SCAN_DEVICE_FINISHED = 0x0002;
        /**等待蓝牙设备启动的最长时间(单位S)*/
        private static final int miWATI_TIME = 10;
        /**每次线程休眠时间(单位ms)*/
        private static final int miSLEEP_TIME = 150;
        /**进程等待提示框*/
        private ProgressDialog mpd = null;

        /**
         * 线程启动初始化操作
         */
        @Override
        public void onPreExecute(){
            /*定义进程对话框*/
            this.mpd = new ProgressDialog(ScanActivity.this);
            this.mpd.setMessage(getString(R.string.ScanActivity_msg_scaning_device));
            this.mpd.setCancelable(true);//可被终止
            this.mpd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            this.mpd.setCanceledOnTouchOutside(true);//点击外部可终止
            this.mpd.setOnCancelListener(new DialogInterface.OnCancelListener(){
                @Override
                public void onCancel(DialogInterface dialog){
                    //按下取消按钮后，终止搜索等待线程
                    _discoveryFinished = true;
                }
            });
            this.mpd.show();

            startSearch(); //执行蓝牙扫描
        }

        @Override
        protected Integer doInBackground(String... params){
            if (!mbluetoothAdapter.isEnabled()) //蓝牙未启动
                return RET_BLUETOOTH_NOT_START;

            int iWait = miWATI_TIME * 1000;//倒减计数器
            //等待miSLEEP_TIME秒，启动蓝牙设备后再开始扫描
            while(iWait > 0){
                if (_discoveryFinished)
                    return RET_SCAN_DEVICE_FINISHED; //蓝牙搜索结束
                else
                    iWait -= miSLEEP_TIME; //剩余等待时间计时
                SystemClock.sleep(miSLEEP_TIME);;
            }
            return RET_SCAN_DEVICE_FINISHED; //在规定时间内，蓝牙设备未启动
        }
        /**
         * 线程内更新处理
         */
        @Override
        public void onProgressUpdate(String... progress){
        }
        /**
         * 阻塞任务执行完后的清理工作
         */
        @Override
        public void onPostExecute(Integer result){
            if (this.mpd.isShowing())
                this.mpd.dismiss();//关闭等待对话框

            if (mbluetoothAdapter.isDiscovering())
                mbluetoothAdapter.cancelDiscovery();

            if (RET_SCAN_DEVICE_FINISHED == result){//蓝牙设备搜索结束

            }else if (RET_BLUETOOTH_NOT_START == result){	//提示蓝牙未启动
                Toast.makeText(ScanActivity.this, getString(R.string.ScanActivity_msg_bluetooth_not_start),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 析构处理
     *   退出时，强制终止搜索
     * */
    @Override
    protected void onDestroy(){
        super.onDestroy();

        if (mbluetoothAdapter.isDiscovering())
            mbluetoothAdapter.cancelDiscovery();
    }
}


