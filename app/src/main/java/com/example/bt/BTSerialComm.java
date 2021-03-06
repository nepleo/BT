package com.example.bt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

//蓝牙串口通信类
public abstract class BTSerialComm {


    //接收缓存大小 ： 50k
    public static final int iBUF_TOTAL = 1024 * 50;
    //接收缓存池
    private final byte[] mbReceiveBufs = new byte[iBUF_TOTAL];
    //接收缓存池指针
    private int miBufDataSite = 0;
    //蓝牙地址码
    private String msMAC;
    //蓝牙连接状态
    private boolean mbConnectOk = false;


    //获取默认适配器
    private BluetoothAdapter mBT = BluetoothAdapter.getDefaultAdapter();
    //蓝牙串口连接对象
    private BluetoothSocket mbsSocket = null;
    //输入流对象
    private InputStream misIn = null;
    //输出流对象
    private OutputStream mosOut = null;
    //接受到的字节数
    private long mlRxd = 0;
    //发送的字节数
    private long mlTxd = 0;
    //建立连接的时间
    private long mlConnEnableTime = 0;
    //断开连接的时间
    private long mlConnDisableTime = 0;

    //接收线程状态,默认不启动接收线程，只有调用接收函数后才启动
    private boolean mbReceiveThread = false;

    //公共接收缓冲区信号量
    private final CResourcePV mresReceiveBuf = new CResourcePV(1);

    //操作开关，强制结束本次接收等待
    private boolean mbKillReceiveData_StopFlg = false;

    /**
     * 常量:未设限制的AsyncTask线程池(重要)
     */
    private static ExecutorService FULL_TASK_EXECUTOR;

    //构造函数
    public BTSerialComm(String sMAC) {
        this.msMAC = sMAC;
    }

    //获取连接保持时间
    public long getConnectHoldTime() {
        if (0 == this.mlConnEnableTime)
            return 0;
        else if (this.mlConnDisableTime == 0)
            return (System.currentTimeMillis() - this.mlConnEnableTime) / 1000;
        else
            return (this.mlConnDisableTime - this.mlConnEnableTime) / 1000;
    }

    //断开蓝牙连接设备
    public void closeConn() {
        if (this.mbConnectOk) {
            try {
                if (null != this.misIn)
                    this.misIn.close();
                if (null != this.mosOut)
                    this.mosOut.close();
                if (null != this.mbsSocket)
                    this.mbsSocket.close();
                this.mbConnectOk = false;//标记连接已被关闭
            } catch (IOException e) {
                //任何一部分报错，都将强制关闭socket连接
                this.misIn = null;
                this.mosOut = null;
                this.mbsSocket = null;
                this.mbConnectOk = false;//标记连接已被关闭
            } finally {    //保存连接中断时间
                this.mlConnDisableTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * 建立蓝牙设备串口连接
     */
    final public boolean createConn() {
        if (!mBT.isEnabled())
            return false;
        //如果连接存在则断开连接
        if (mbConnectOk)
            this.closeConn();

        //开始连接蓝牙设备
        final BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(this.msMAC);

        //UUID连接
         UUID uuidSPP = UUID.fromString(BluetoothSppClient.UUID_SPP);

        try {

            this.mbsSocket = device.createRfcommSocketToServiceRecord(uuidSPP);
            this.mbsSocket.connect();
            this.mosOut = this.mbsSocket.getOutputStream();
            this.misIn = this.mbsSocket.getInputStream();
            this.mbConnectOk = true;
            this.mlConnEnableTime = System.currentTimeMillis();
        } catch (IOException e) {
            this.closeConn(); //断开连接
            return false;
        } finally {
            this.mlConnDisableTime = 0; //连接终止时间初始化
        }
        return true;
    }

/**
 * 判断当前设备是否连接
* */

    public boolean isConnect() {
        return this.mbConnectOk;
    }

    /**
     * 接收到的字节数
    * */
    public long getRxd() {
        return this.mlRxd;
    }

    /**
     * 发送的字节数
    * */
    public long getTxd() {
        return this.mlTxd;
    }

    //接收缓冲池的数据量
    public int getReceiveBufLen() {
        int iBufSize = 0;
        this.P(this.mresReceiveBuf);
        iBufSize = this.miBufDataSite;
        this.V(this.mresReceiveBuf);
        return iBufSize;
    }

    //发送数据 >=0 发送成功  -2：连接未建立  -3:连接丢失
    protected int SendData(byte[] btData) {
        if (this.mbConnectOk) {
            try {
                mosOut.write(btData);
                this.mlTxd += btData.length;
                return btData.length;
            } catch (IOException e) {
                //连接丢失
                this.closeConn();
                return -3;
            }
        } else
            return -2;
    }

    //接收数据 null：未连接 / byte 取到新的数据
    final protected synchronized byte[] ReceiveData(){
        byte[] btBufs = null;
        if (mbConnectOk){
            if (!this.mbReceiveThread){

                //启动接收线程
                // new ReceiveThread().execute("");

                return null; //首次启动线程直接返回空字符串
            }

            this.P(this.mresReceiveBuf);//夺取缓存访问权限
            if (this.miBufDataSite > 0){
                btBufs = new byte[this.miBufDataSite];
                for(int i=0; i<this.miBufDataSite; i++)
                    btBufs[i] = this.mbReceiveBufs[i];
                this.miBufDataSite = 0;
            }
            this.V(this.mresReceiveBuf);//归还缓存访问权限
        }
        return btBufs;
    }

    /**
     * 比较两个Byte数组是否相同
     * @param src 源数据
     * @param dest 目标数据
     * @return boolean
     * */
    private static boolean CompByte(byte[] src, byte[] dest){
        if (src.length != dest.length)
            return false;

        for (int i=0, iLen=src.length; i<iLen; i++)
            if (src[i] != dest[i])
                return false;//当前位发现不同
        return true;//未发现差异
    }

    /**
     * 接收数据（带结束标识符的接收方式）<br />
     * <strong>注意:</strong>本函数以阻塞模式工作，如果未收到结束符，将一直等待。<br />
     * <strong>备注:</strong>只有遇到结束标示符时才会终止等待，并送出结果。适合于命令行模式。<br />
     * 如果想要终止柱塞等待可调用killReceiveData_StopFlg()
     * @param btStopFlg 结束符 (例如: '\n')
     * @return null:未连接或连接中断/byte[]:取到数据
     * */
    final protected byte[] ReceiveData_StopFlg(byte[] btStopFlg){
        int iStopCharLen = btStopFlg.length; //终止字符的长度
        int iReceiveLen = 0; //临时变量，保存接收缓存中数据的长度
        byte[] btCmp = new byte[iStopCharLen];
        byte[] btBufs = null; //临时输出缓存

        if (mbConnectOk){
            if (!this.mbReceiveThread){
                //启动接收线程
                new ReceiveThread().execute("");
                SystemClock.sleep(50);//延迟，给线程启动的时间
            }

            while(true){
                this.P(this.mresReceiveBuf);//夺取缓存访问权限
                iReceiveLen = this.miBufDataSite - iStopCharLen;
                this.V(this.mresReceiveBuf);//归还缓存访问权限
                if (iReceiveLen > 0)
                    break; //发现数据结束循环等待
                else
                    SystemClock.sleep(50);//等待缓冲区被填入数据（死循环等待）
            }


            //当缓冲池收到数据后，开始等待接收数据段
            this.mbKillReceiveData_StopFlg = false; //可用killReceiveData_StopFlg()来终止阻塞状态
            while(this.mbConnectOk && !this.mbKillReceiveData_StopFlg){
                /*复制末尾待检查终止符*/
                this.P(this.mresReceiveBuf);//夺取缓存访问权限
                for(int i=0; i<iStopCharLen; i++)
                    btCmp[i] = this.mbReceiveBufs[this.miBufDataSite - iStopCharLen + i];
                this.V(this.mresReceiveBuf);//归还缓存访问权限

                if (CompByte(btCmp,btStopFlg)){ //检查是否为终止符
                    //取出数据时，去掉结尾的终止符
                    this.P(this.mresReceiveBuf);//夺取缓存访问权限
                    btBufs = new byte[this.miBufDataSite-iStopCharLen]; //分配存储空间
                    for(int i=0, iLen=this.miBufDataSite-iStopCharLen; i<iLen; i++)
                        btBufs[i] = this.mbReceiveBufs[i];
                    this.miBufDataSite = 0;
                    this.V(this.mresReceiveBuf);//归还缓存访问权限
                    break;
                }
                else
                    SystemClock.sleep(10);//死循环，等待数据回复
            }
        }
        return btBufs;
    }

    /**
     * 强制终止ReceiveData_StopFlg()的阻塞等待状态
     * @return void
     * @see 必须在ReceiveData_StopFlg()执行后，才有使用价值
     * */
    public void killReceiveData_StopFlg(){
        this.mbKillReceiveData_StopFlg = true;
    }

    /**
     * 互斥锁P操作：夺取资源
     * @param res CResourcePV 资源对象
     * */
    private void P(CResourcePV res){
        while(!res.seizeRes())
            SystemClock.sleep(2);//资源被占用，延迟检查
    }
    /**
     * 互斥锁V操作：释放资源
     *  @param res CResourcePV 资源对象
     * */
    private void V(CResourcePV res){
        res.revert(); //归还资源
    }

    //----------------
    /*多线程处理*/
    private class ReceiveThread extends AsyncTask<String, String, Integer> {
        /**常量:缓冲区最大空间*/
        static private final int BUFF_MAX_CONUT = 1024*5;
        /**常量:连接丢失*/
        static private final int CONNECT_LOST = 0x01;
        /**常量：接收线程正常结束*/
        static private final int THREAD_END = 0x02;

        /**
         * 线程启动初始化操作
         */
        @Override
        public void onPreExecute(){
            mbReceiveThread = true;//标记启动接收线程
            miBufDataSite = 0; //缓冲池指针归0
        }

        @Override
        protected Integer doInBackground(String... arg0){
            int iReadCnt = 0; //本次读取的字节数
            byte[] btButTmp = new byte[BUFF_MAX_CONUT]; //临时存储区


            /*只要连接建立完成就开始进入读取等待处理*/
            while(mbConnectOk){
                try{
                    iReadCnt = misIn.read(btButTmp); //没有数据，将一直锁死在这个位置等待
                }catch (IOException e){
                    return CONNECT_LOST;
                }

                //开始处理接收到的数据
                P(mresReceiveBuf);//夺取缓存访问权限
                mlRxd += iReadCnt; //记录接收的字节总数
                /*检查缓冲池是否溢出，如果溢出则指针标志位归0*/
                if ( (miBufDataSite + iReadCnt) > iBUF_TOTAL)
                    miBufDataSite = 0;
                /*将取到的数据复制到缓冲池中*/
                for(int i=0; i<iReadCnt; i++)
                    mbReceiveBufs[miBufDataSite + i] = btButTmp[i];
                miBufDataSite += iReadCnt; //保存本次接收的数据长度
                V(mresReceiveBuf);//归还缓存访问权限
            }
            return THREAD_END;
        }

        /**
         * 阻塞任务执行完后的清理工作
         */
        @Override
        public void onPostExecute(Integer result){
            mbReceiveThread = false;//标记接收线程结束
            if (CONNECT_LOST == result){
                //判断是否为串口连接失败
                closeConn();
            }else{	//正常结束，关闭接收流
                try{
                    misIn.close();
                    misIn = null;
                }catch (IOException e){
                    misIn = null;
                }
            }
        }
    }
}
