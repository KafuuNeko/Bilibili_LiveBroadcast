import Util.HttpUtils;
import Util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.*;

public class Main {
    public static List<Connect> mConnect = new ArrayList<>();
    public static Timer mRefreshList = null;
    private static int mMode = 0;

    public static void main(String[] arg) {

        //initConnect(true);
        System.out.print("监测模式（1:自动获取房间，2:自动获取房间-每个分区只随机获取一个房间，3:不自动获取）：");
        mMode = new Scanner(System.in).nextInt();
        if (mMode != 1 && mMode != 2) System.out.println("您可以使用指令join [房间号]加入指定房间监测");

        initConnect(false);
        loop();

        for (Connect conn : mConnect) conn.close();
    }

    private static void initConnect(boolean stress)
    {
        if (stress) {
            for (long room = 1000000; room <= 1003000; ++room) {
                Connect connect = new Connect(room, "broadcastlv.chat.bilibili.com", 2243);
                mConnect.add(connect);
            }
        }else
        {
            if (mMode == 1 || mMode == 2)
            {
                initTimer();
            }
        }
    }

    private static void initTimer()
    {
        mRefreshList = new Timer();
        mRefreshList.schedule(new TimerTask() {
            @Override
            public void run() {
                if (mMode == 1)
                {
                    Refresh();
                }else if (mMode == 2) {
                    Refresh_v2();
                }
            }
        }, 1,3600000);
    }

    private static void loop()
    {
        Scanner scanner = new Scanner(System.in);
        String input;
        do {
            input = scanner.next();

            if (input.equals("join"))
            {
                Connect connect = new Connect(scanner.nextLong());
                if (connect.isOpen())
                {
                    mConnect.add(connect);
                }
                else
                {
                    Log.Info("Join", "添加检测房间失败");
                }
            }

        }while (!input.equals("exit"));
    }

    public static boolean Refresh()
    {
        List<Connect> new_list = new ArrayList<>();

        Gson gson = new Gson();
        JsonObject result = gson.fromJson(HttpUtils.sendGet("https://api.live.bilibili.com/room/v3/area/getRoomList?platform=web"), JsonObject.class);
        if (result.get("code").getAsInt() != 0)
        {
            return false;
        }

        JsonArray list = result.getAsJsonObject("data").getAsJsonArray("list");

        int number = list.size();
        for (int i = 0; i < number; ++i)
        {
            long room_id = list.get(i).getAsJsonObject().get("roomid").getAsLong();

            Connect connect = new Connect(room_id);
            if (connect.isOpen())
            {
                Log.Info("Join Success", "Room_id:" + room_id);
                new_list.add(connect);
            }
            else
            {
                Log.Info("Join Fail", "Room_id:" + room_id);
            }

        }

        load_new_list(new_list);

        return true;
    }

    public static boolean Refresh_v2()
    {
        List<Connect> new_list = new ArrayList<>();

        Gson gson = new Gson();

        for (int parent_area_id = 1; parent_area_id <= 6; ++parent_area_id)
        {
            JsonObject result = gson.fromJson(HttpUtils.sendGet("https://api.live.bilibili.com/room/v3/area/getRoomList?platform=web&parent_area_id="+parent_area_id), JsonObject.class);
            if (result.get("code").getAsInt() != 0) continue;
            JsonArray list = result.getAsJsonObject("data").getAsJsonArray("list");
            int number = list.size();

            long room_id = list.get(new Random().nextInt(number)).getAsJsonObject().get("roomid").getAsLong();

            Connect connect = new Connect(room_id);
            if (connect.isOpen())
            {
                Log.Info("Join Success", "Room_id:" + room_id);
                new_list.add(connect);
            }
            else
            {
                Log.Info("Join Fail", "Room_id:" + room_id);
            }
        }

        load_new_list(new_list);

        return true;
    }

    public static void load_new_list(List<Connect> new_list)
    {
        for (Connect conn : mConnect)
        {
            if (conn.isOpen())
            {
                boolean success = conn.close();
                if (!success)
                {
                    new_list.add(conn);
                    Log.Info("关闭 RoomID:"+conn.getRoomID(), "关闭失败，加入新监测列表");
                }
            }
        }

        mConnect = new_list;
    }


}
