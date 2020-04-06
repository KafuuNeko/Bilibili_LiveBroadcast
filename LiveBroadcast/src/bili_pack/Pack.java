package bili_pack;

public class Pack {

    public final static int head_len = 16;

    public static byte[] make_head(int version, int op, int seq, int data_len)
    {
        int i = 0;

        byte[] result = new byte[head_len];
        int pack_len = head_len + data_len;

        //封包总大小
        for(int j = 0; j < 4; ++j) result[i++] = (byte)(pack_len>>((3-j) * 8) & 0xFF);

        //头部长度
        result[i++] = (byte)(head_len >> 8);
        result[i++] = (byte)(head_len);

        //协议版本
        result[i++] = (byte)(version>>8);
        result[i++] = (byte)(version);

        //操作码
        for(int j = 0; j < 4; ++j) result[i++] = (byte)(op>>((3-j) * 8) & 0xFF);

        //SeqMake
        for(int j = 0; j < 4; ++j) result[i++] = (byte)(seq>>((3-j) * 8) & 0xFF);

        return result;
    }

    public static HeadInfo unpack_head(byte[] head)
    {
        //System.out.println(Arrays.toString(head));
        int i = 0;
        int temp = 0;

        int len = 0;
        for(int j = 0; j < 4; ++j)
        {
            temp = head[i++] & 0xFF;

            len <<= 8;
            len |= temp;
        }

        i += 2;//这里是包头长度，忽略

        //获取协议版本
        int versions = 0;
        for(int j = 0; j < 2; ++j)
        {
            temp = head[i++] & 0xFF;

            versions <<= 8;
            versions |= temp;

        }

        //获取操作码
        int operation = 0;
        for(int j = 0; j < 4; ++j)
        {
            temp = head[i++] & 0xFF;

            operation <<= 8;
            operation |= temp;
        }

        return new HeadInfo(versions,operation,len);
    }

}
