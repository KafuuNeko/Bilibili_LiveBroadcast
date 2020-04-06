import Util.HttpUtils;
import Util.Log;
import Util.ZLibUtils;
import bili_pack.HeadInfo;
import bili_pack.Pack;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;


public class Connect {
    enum Status {
        Running,Stop
    }

    private final int BUF_MAX_SIZE = 512;

    private Status mStatus;

    private SocketChannel mClickSocket;
    private ByteBuffer mReceiveBuffer = ByteBuffer.allocate(BUF_MAX_SIZE);

    //接收包数据相关
    private int mPackResidue = 0;//接收包剩余数据
    private byte[] mPackHead = new byte[Pack.head_len];//接收包头信息Buf
    private int mPackHeadIndex = 0;//当前需填入接收包头信息buf的索引
    private HeadInfo mPackHeadInfo = null;//接受包头信息
    private ByteArrayOutputStream mPackDataBuff = null;//接收包数据缓存

    private long mRoomID;

    private Timer mHeartTimer = new Timer();

    public Connect(long room_id)
    {

        mRoomID = room_id;
        if(InitSocket(room_id))
        {
            mStatus = Status.Running;
            InitReceive();
            Attestation(room_id);
            InitHeart();
        }

    }

    public Connect(long room_id, String host, int port)
    {
        mRoomID = room_id;

        try {
            mClickSocket = SocketChannel.open(new InetSocketAddress(host, port));
        }catch (Exception e)
        {
            Log.Error("连接 Room:"+room_id, "连接通讯服务器失败");
        }

        mStatus = Status.Running;
        InitReceive();
        Attestation(room_id);
        InitHeart();
    }

    /*
    * 初始化与连接Socket
    * */
    private boolean InitSocket(long room_id)
    {
        try {
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(HttpUtils.sendGet("https://api.live.bilibili.com/room/v1/Danmu/getConf?room_id="+room_id+"&platform=pc&player=web"), JsonObject.class);
            if(jsonObject.get("code").getAsInt() != 0) return false;

            jsonObject = jsonObject.getAsJsonObject("data");

            String host = jsonObject.get("host").getAsString();
            int port = jsonObject.get("port").getAsInt();

            mClickSocket = SocketChannel.open(new InetSocketAddress(host, port));

            return true;
        }catch (Exception e)
        {
            Log.Error("连接 Room:"+room_id, "连接通讯服务器失败");
            return false;
        }

    }
    /*
    * 初始化接收器
    * */
    private void InitReceive()
    {
        Thread mTaskThread = new Thread(() -> {
            try {

                int len;

                while (mStatus == Status.Running && (len = mClickSocket.read(mReceiveBuffer)) > 0) {
                    dispose_receive(mReceiveBuffer.array(), len);
                    mReceiveBuffer.clear();
                }

                Log.Info("Receive Room:" + mRoomID, "已与服务器断开连接");
            } catch (IOException e) {
                Log.Error("Receive Room:" + mRoomID, e.toString());
            }finally {
                close();//关闭
            }

        });
        mTaskThread.start();
    }

    /*
    * 发送认证包
    * */
    private void Attestation(long room_id)
    {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("roomid", room_id);
        Send(jsonObject.toString(), 1,7);
    }

    /*
    * 初始化心跳包
    * */
    private void InitHeart()
    {
        mHeartTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Send("", 2, 2);
            }
        }, 1000*30, 1000*30);
    }

    private void dispose_receive(byte[] data, int n) throws IOException
    {
        //Util.Log.Info("原", new String(data, 0, n));
        for (int i = 0; i < n; ++i)
        {
            if (mPackHeadIndex < Pack.head_len)
            {
                mPackHead[mPackHeadIndex++] = data[i];
            }
            else
            {
                if (mPackHeadInfo == null)
                {
                    //System.out.println(Arrays.toString(mPackHead));
                    mPackHeadInfo = Pack.unpack_head(mPackHead);
                    mPackResidue = mPackHeadInfo.length - Pack.head_len;
                    //System.out.println(mPackResidue);
                    mPackDataBuff = new ByteArrayOutputStream();
                }

                if (mPackResidue > 0)
                {
                    mPackDataBuff.write(data[i]);
                }

                if(--mPackResidue <= 0)
                {
                    assert mPackDataBuff != null;
                    mPackDataBuff.flush();
                    fail_receive(mPackHeadInfo, mPackDataBuff.toByteArray());

                    mPackDataBuff.close();
                    mPackResidue = 0;
                    mPackHeadIndex = 0;
                    mPackHeadInfo = null;
                    mPackDataBuff = null;
                }

            }
        }
    }

    private void fail_receive(HeadInfo head, byte[] data)
    {
        //Log.Info("接收 Room:"+mRoomID, head.toString());
        if (head.operation == 3)
        {
            //Log.Info("接收 Room:"+mRoomID, "心跳回应");
        }else if (head.operation == 8)
        {
            Log.Info("接收 Room:"+mRoomID, "认证响应：" + new String(data, 0, data.length));
        }else if (head.operation == 5)
        {
            if (head.versions == 2) data = ZLibUtils.decompress(data);
            String res = new String(data, 0, data.length, StandardCharsets.UTF_8);

            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(res, JsonObject.class);
            String cmd = jsonObject.get("cmd").getAsString();


            if (cmd.equals("DANMU_MSG"))
            {
                JsonArray jsonArray = jsonObject.getAsJsonArray("info");
                Log.Info("弹幕 Room:"+mRoomID, jsonArray.get(1).getAsString());
            }
            else {
                //Log.Info("其它 Room:"+mRoomID, res);
            }
        }
    }

    public void Send(String msg, int ver, int op)
    {
        try{

            byte[] data_bytes = msg.getBytes();
            byte[] head_data = Pack.make_head(ver, op, 1, data_bytes.length);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(head_data);
            bos.write(data_bytes);
            bos.flush();

            mClickSocket.write(ByteBuffer.wrap(bos.toByteArray()));

        }catch (IOException e)
        {
            Log.Info("Send Room:"+mRoomID, e.toString());
        }

    }

    public boolean isOpen()
    {
        return mStatus == Status.Running;
    }

    public boolean close()
    {
        try {
            mClickSocket.close();
            mHeartTimer.cancel();

            mStatus = Status.Stop;
            return true;
        }catch (IOException e)
        {
            return false;
        }

    }

    public long getRoomID()
    {
        return mRoomID;
    }
}
