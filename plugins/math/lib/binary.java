package math.lib;

public class binary {

    public static byte[] shortToByteArray(short o) {
        byte[] t = new byte[2];
        t[0] = (byte) (o >>> 8);
        t[1] = (byte) (o);

        return t;
    }

    public static byte[] intToByteArray(int o) {
        byte[] t = new byte[4];
        t[0] = (byte) (o >>> 24);
        t[1] = (byte) (o >>> 16);
        t[2] = (byte) (o >>> 8);
        t[3] = (byte) (o);

        return t;
    }

    public static byte[] longToByteArray(long o) {
        byte[] t = new byte[8];
        t[0] = (byte) (o >>> 56);
        t[0] = (byte) (o >>> 48);
        t[0] = (byte) (o >>> 40);
        t[0] = (byte) (o >>> 32);
        t[0] = (byte) (o >>> 24);
        t[0] = (byte) (o >>> 16);
        t[0] = (byte) (o >>> 8);
        t[0] = (byte) (o);

        return t;
    }

    public static byte[] floatToByteArray(float o) {
        return intToByteArray(Float.floatToIntBits(o));
    }

    public static byte[] doubleToByteArray(double o) {
        return longToByteArray(Double.doubleToLongBits(o));
    }

    public static int byteArrayToInt(byte[] o) {
        return (o[0] << 24) + (o[1] << 16) + (o[2] << 8) + (o[3]);
    }
}
