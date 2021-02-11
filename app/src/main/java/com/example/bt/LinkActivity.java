package com.example.bt;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Hashtable;

import javax.crypto.Mac;


public class LinkActivity extends AppCompatActivity {


    private Hashtable<String, String> mhtDeviceInfo = new Hashtable<String, String>();

    /**蓝牙配对进程操作标志*/
    private boolean mbBonded = false;

    /**蓝牙解除配对进程操作标志*/
    private boolean mbUnBonded = false;

    //控件:配对按钮
    private Button mbtnPair = null;

    private EditText meditText = null;
    //控件:通信按钮
    private Button mbtnCon = null;

    /**获取到的UUID Service 列表信息*/
    private ArrayList<String> mslUuidList = new ArrayList<String>();

    /**手机的蓝牙适配器*/
    private BluetoothAdapter mBT = BluetoothAdapter.getDefaultAdapter();
    /**蓝牙设备连接句柄*/
    private BluetoothDevice mBDevice = null;

    //控件:Device Info显示区
    private TextView mtvDeviceInfo = null;
    //控件uuid显示区
    private TextView mtvServiceUUID = null;


    /**蓝牙SPP通信连接对象*/
    public BluetoothSppClient mBSC = null;

    /**日志提示标签*/
    private String TAG = "Log Warning :";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link);
        Intent intent = getIntent();
        this.mhtDeviceInfo.put("MAC", intent.getStringExtra("MAC"));
        this.mhtDeviceInfo.put("RSSI", intent.getStringExtra("RSSI"));
        this.mhtDeviceInfo.put("NAME", intent.getStringExtra("NAME"));
        this.mhtDeviceInfo.put("COD", intent.getStringExtra("COD"));
        this.mhtDeviceInfo.put("BOND", intent.getStringExtra("BOND"));
        this.mhtDeviceInfo.put("DEVICE_TYPE", intent.getStringExtra("DEVICE_TYPE"));
        this.mtvDeviceInfo = (TextView) this.findViewById(R.id.device_info);
        this.mtvServiceUUID = (TextView) this.findViewById(R.id.service_uuid);
        this.mbtnPair = (Button) this.findViewById(R.id.pair_btn);
        this.meditText = (EditText) this.findViewById(R.id.edit_text_tx);
        this.mbtnCon = (Button) this.findViewById(R.id.btnCon);

        this.mBDevice = mBT.getRemoteDevice(this.mhtDeviceInfo.get("MAC"));
        this.mBSC = new BluetoothSppClient(this.mhtDeviceInfo.get("MAC"));

        if (this.mhtDeviceInfo.get("BOND").equals("Unbond")) {
            this.mbtnCon.setEnabled(false);
        } else {
            this.mbtnPair.setEnabled(false);
        }
        //显示蓝牙设备
        this.showDeviceInfo();

    }

    /**
     * 析构处理
     * */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.mBSC.closeConn();//关闭连接
    }


    /**
     * 显示选中设备的信息
     * */
    private void showDeviceInfo(){
        /*显示需要连接的设备信息*/
        this.mtvDeviceInfo.setText(
                String.format(getString(R.string.link_device_info),
                        this.mhtDeviceInfo.get("NAME"),
                        this.mhtDeviceInfo.get("MAC"),
                        this.mhtDeviceInfo.get("COD"),
                        this.mhtDeviceInfo.get("RSSI"),
                        this.mhtDeviceInfo.get("DEVICE_TYPE"),
                        this.mhtDeviceInfo.get("BOND"))
        );
    }


    /** 广播监听:蓝牙配对处理 */
    private BroadcastReceiver _mPairingRequest = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent){
            BluetoothDevice device = null;
            if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)){	//配对状态改变时，的广播处理
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() == BluetoothDevice.BOND_BONDED)
                    mbBonded = true;//蓝牙配对设置成功
                else if (device.getBondState() == BluetoothDevice.BOND_NONE)
                    mbUnBonded = true;
            }
        }
    };



    /** 广播监听:获取UUID */
    private BroadcastReceiver _mGetUuidServiceReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent intent){
            String action = intent.getAction();
            int iLoop = 0;
            if (BluetoothDevice.ACTION_UUID.equals(action)){
                Parcelable[] uuidExtra =
                        intent.getParcelableArrayExtra("android.bluetooth.device.extra.UUID");
                if (null != uuidExtra)
                    iLoop = uuidExtra.length;
                /*uuidExtra should contain my service's UUID among his files, but it doesn't!!*/
                for(int i=0; i<iLoop; i++)
                    mslUuidList.add(uuidExtra[i].toString());
            }
        }
    };

    /**
     * 显示Service UUID信息
     * */
    private void showServiceUUIDs(){

            new GetUUIDServiceTask().execute("");

    }

    /**
     * 显示UUID的单击事件
     * */
    public void showUUIDs(View v){

        new GetUUIDServiceTask().execute("");

    }


    /**
     * 配对按钮的单击事件
     * */
    public void btnPair(View v){
        new PairTask().execute(this.mhtDeviceInfo.get("MAC"));
        this.mbtnPair.setEnabled(false); //冻结配对按钮
    }


    /**
     * 取消配对按钮的单击事件
     * */
    public void btnUnPair(View v) {
        new UnPairTask().execute(this.mhtDeviceInfo.get("MAC"));
    }

    /**
     * 发送信息按钮
     * */
    public void btnSend(View v) {
        this.meditText = (EditText) findViewById(R.id.edit_text_tx);
        String tx = meditText.getText().toString();
        if ((this.mBSC.Send(tx)) >= 0) {
            Toast.makeText(LinkActivity.this,
                    "Send:" + tx,
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(LinkActivity.this,
                    getString(R.string.LinkActivity_msg_connect_again),
                    Toast.LENGTH_LONG).show();
        }
            this.meditText.setText(null);
    }

    /**
     * 建立设备的串行通信连接
     * */
    public void btnConn(View v){
        //showServiceUUIDs();//显示远程设备提供的服务
        new connSocketTask().execute(this.mBDevice.getAddress());
    }


    //配对线程
    private class PairTask extends AsyncTask<String, String, Integer> {
        /**常量:配对成功*/
        static private final int RET_BOND_OK = 0x00;
        /**常量: 配对失败*/
        static private final int RET_BOND_FAIL = 0x01;
        /**常量: 配对等待时间(15秒)*/
        static private final int iTIMEOUT = 1000 * 15;
        /**
         * 线程启动初始化操作
         */
        @Override
        public void onPreExecute(){
            //提示开始建立配对
            Toast.makeText(LinkActivity.this,
                    getString(R.string.LinkActivity_msg_bluetooth_Bonding),
                    Toast.LENGTH_SHORT).show();
            //蓝牙自动配对
            //监控蓝牙配对请求
            registerReceiver(_mPairingRequest, new IntentFilter(BluetoothCtrl.PAIRING_REQUEST));
            //监控蓝牙配对是否成功
            registerReceiver(_mPairingRequest, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        }

        @Override
        protected Integer doInBackground(String... arg0){
            final int iStepTime = 150;
            int iWait = iTIMEOUT; //设定超时等待时间
            try{	//开始配对
                //获得远端蓝牙设备
                //mBDevice = mBT.getRemoteDevice(arg0[0]);
                BluetoothCtrl.createBond(mBDevice);
                mbBonded = false; //初始化配对完成标志
            }catch (Exception e1){	//配对启动失败
                Log.d(getString(R.string.app_name), "create Bond failed!");
                e1.printStackTrace();
                return RET_BOND_FAIL;
            }
            while(!mbBonded && iWait > 0){
                SystemClock.sleep(iStepTime);
                iWait -= iStepTime;
            }
            return (int) ((iWait > 0)? RET_BOND_OK : RET_BOND_FAIL);
        }

        /**
         * 阻塞任务执行完后的清理工作
         */
        @Override
        public void onPostExecute(Integer result){
            unregisterReceiver(_mPairingRequest); //注销监听

            if (RET_BOND_OK == result){//配对建立成功
                Toast.makeText(LinkActivity.this,
                        getString(R.string.LinkActivity_msg_bluetooth_Bond_Success),
                        Toast.LENGTH_SHORT).show();
                mhtDeviceInfo.put("BOND", getString(R.string.Scan_bond_bonded));//显示已绑定
                showDeviceInfo();//刷新配置信息
                showServiceUUIDs();//显示远程设备提供的服务
                mbtnCon.setEnabled(true);
            }else{	//在指定时间内未完成配对
                Toast.makeText(LinkActivity.this,
                        getString(R.string.LinkActivity_msg_bluetooth_Bond_fail),
                        Toast.LENGTH_LONG).show();
                try{
                    BluetoothCtrl.removeBond(mBDevice);
                }catch (Exception e){
                    Log.d(getString(R.string.app_name), "removeBond failed!");
                    e.printStackTrace();
                }
                mbtnPair.setEnabled(true); //解冻配对按钮
            }
        }
    }


    /**
    * 解除蓝牙配对
    * */
    private class UnPairTask extends AsyncTask<String, String, Integer> {
        //解除配对成功
        static private final int REMOVE_BOND_OK = 0x00;
        //解除配对失败
        static private final int REMOVE_BOND_FAIL = 0x01;
        //常量: 配对等待时间(10秒)*/
        static private final int iTIMEOUT = 1000 * 10;
        @Override
        public void onPreExecute(){
            Toast.makeText(LinkActivity.this,
                    getString(R.string.LinkActivity_msg_bluetooth_RemoveBond),
                    Toast.LENGTH_SHORT).show();
            mBSC.closeConn();//关闭连接
            //监控蓝牙配对状态请求
            registerReceiver(_mPairingRequest, new IntentFilter(BluetoothCtrl.PAIRING_REQUEST));
            //监控蓝牙配对状态是否成功
            registerReceiver(_mPairingRequest, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));


        }

        @Override
        protected Integer doInBackground(String... arg0) {
            final int iStepTime = 150;
            int iWait = iTIMEOUT; //设定超时等待时间
            try{
                //获得远端蓝牙设备
                //mBDevice = mBT.getRemoteDevice(arg0[0]);
                BluetoothCtrl.removeBond(mBDevice);
                mbUnBonded = false;
            }catch (Exception e1){	//配对启动失败
                Log.d(getString(R.string.app_name), "Remove Bond failed!");
                e1.printStackTrace();
                return REMOVE_BOND_FAIL;
            }
            while( !mbUnBonded && iWait > 0){
                SystemClock.sleep(iStepTime);
                iWait -= iStepTime;
            }
            return (int) ((iWait > 0)? REMOVE_BOND_OK : REMOVE_BOND_FAIL);
        }

        @Override
        public void onPostExecute(Integer result) {
            unregisterReceiver(_mPairingRequest); //注销监听
            try {
                if (result == REMOVE_BOND_OK) {
                    mhtDeviceInfo.put("BOND", getString(R.string.Scan_bond_nothing));//显示UnBond
                    Toast.makeText(LinkActivity.this,
                            getString(R.string.LinkActivity_msg_bluetooth_removeBond_succes),
                            Toast.LENGTH_LONG).show();
                    mbtnCon.setEnabled(false);
                    showDeviceInfo();//刷新配置信息
                } else {
                    Toast.makeText(LinkActivity.this,
                            getString(R.string.LinkActivity_msg_bluetooth_removeBond_fail),
                            Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            mbtnPair.setEnabled(true); //解冻配对按钮
        }
    }



    /*多线程处理(读取UUID Service信息线程)*/
    private class GetUUIDServiceTask extends AsyncTask<String, String, Integer>{
        /**延时等待时间*/
        private static final int miWATI_TIME = 4 * 1000;
        /**每次检测的时间*/
        private static final int miREF_TIME = 200;
        /**uuis find service is run*/
        private boolean mbFindServiceIsRun = false;
        /**
         * 线程启动初始化操作
         */
        @Override
        public void onPreExecute(){
            mslUuidList.clear();
            //提示UUID服务搜索中
            mtvServiceUUID.setText(getString(R.string.LinkActivity_find_service_uuids));

            //mtvServiceUUID.setVisibility(View.INVISIBLE);
            // Don't forget to unregister during onDestroy
            registerReceiver(_mGetUuidServiceReceiver,
                    new IntentFilter(BluetoothDevice.ACTION_UUID));// Register the BroadcastReceiver
            this.mbFindServiceIsRun = mBDevice.fetchUuidsWithSdp();
        }

        /**
         * 线程异步处理
         */
        @Override
        protected Integer doInBackground(String... arg0){
            int iWait = miWATI_TIME;//倒减计数器

            if (!this.mbFindServiceIsRun)
                return null; //UUID Service扫瞄服务器启动失败

            while(iWait > 0){
                if (mslUuidList.size() > 0 && iWait > 1500)
                    iWait = 1500; //如果找到了第一个UUID则继续搜索N秒后结束
                SystemClock.sleep(miREF_TIME);
                iWait -= miREF_TIME;//每次循环减去刷新时间
            }
            return null;
        }
        /**
         * 阻塞任务执行完后的清理工作
         */
        @Override
        public void onPostExecute(Integer result){
            StringBuilder sbTmp = new StringBuilder();
            unregisterReceiver(_mGetUuidServiceReceiver); //注销广播监听
            //如果存在数据，则自动刷新
            if (mslUuidList.size() > 0){
                for(int i=0; i<mslUuidList.size(); i++)
                    sbTmp.append(mslUuidList.get(i) + "\n");
                mtvServiceUUID.setText(sbTmp.toString());
                //TODO:显示uuid textview
                //不显示uuid
                //mtvServiceUUID.setVisibility(View.INVISIBLE);
            }else//未发现UUID服务列表
                mtvServiceUUID.setText(R.string.LinkActivity_not_find_service_uuids);
                //不显示uuid
               //mtvServiceUUID.setVisibility(View.INVISIBLE);
        }
    }


    /*多线程处理(建立蓝牙设备的串行通信连接)*/
    private class connSocketTask extends AsyncTask<String, String, Integer>{
        /**进程等待提示框*/
        private ProgressDialog mpd = null;
        /**常量:连接建立失败*/
        private static final int CONN_FAIL = 0x01;
        /**常量:连接建立成功*/
        private static final int CONN_SUCCESS = 0x02;

        /**
         * 线程启动初始化操作
         */
        @Override
        public void onPreExecute(){
            /*定义进程对话框*/
            this.mpd = new ProgressDialog(LinkActivity.this);
            this.mpd.setMessage(getString(R.string.LinkActivity_msg_device_connecting));
            this.mpd.setCancelable(false);//可被终止
            this.mpd.setCanceledOnTouchOutside(false);//点击外部可终止
            this.mpd.show();
        }

        @Override
        protected Integer doInBackground(String... arg0){

            mBSC = new BluetoothSppClient(arg0[0]);
            if (mBSC.createConn())
                return CONN_SUCCESS; //建立成功
            else
                return CONN_FAIL; //建立失败
        }/**/

        /**
         * 阻塞任务执行完后的清理工作
         */
        @Override
        public void onPostExecute(Integer result){
            this.mpd.dismiss();
            //showServiceUUIDs();
            if (CONN_SUCCESS == result){	//通信连接建立成功
                Toast.makeText(LinkActivity.this,
                        getString(R.string.LinkActivity_msg_device_connect_succes),
                        Toast.LENGTH_SHORT).show();
            }else{	//通信连接建立失败
                Toast.makeText(LinkActivity.this,
                        getString(R.string.LinkActivity_msg_device_connect_fail),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}