package bili_pack;

public class HeadInfo {
    public int versions;
    public int operation;
    public int length;

    public HeadInfo(int versions, int operation, int length)
    {
        this.versions = versions;
        this.operation = operation;
        this.length = length;
    }

    public String toString()
    {
        return "Version:"+ this.versions+",operation:"+this.operation+",length:"+this.length;
    }

}
