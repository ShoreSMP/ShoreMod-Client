package net.shoresmp.core;
import java.io.*;
import java.net.URI;
import java.util.regex.Pattern;
/** Separate optional channel: legacy clients and roster protocol remain compatible. */
public final class ModUpdates {
    public static final String CHANNEL="shoremod:updates";
    private static final Pattern VERSION=Pattern.compile("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}(?:-[A-Za-z0-9.-]{1,40})?");
    public record Notice(String version,String url) {
        public Notice {if(!validVersion(version)||!validUrl(url))throw new IllegalArgumentException("Invalid update announcement");}
    }
    public static boolean validVersion(String v){return v!=null&&VERSION.matcher(v).matches();}
    public static boolean validUrl(String u){
        if(u==null||u.length()>2048)return false;if(u.isEmpty())return true;
        try{URI uri=URI.create(u);return "https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getRawUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443);}catch(IllegalArgumentException e){return false;}
    }
    public static boolean newer(String candidate,String installed){
        if(!validVersion(candidate)||!validVersion(installed))return false;
        String[] a=candidate.split("-",2),b=installed.split("-",2),an=a[0].split("\\."),bn=b[0].split("\\.");
        for(int i=0;i<3;i++){int c=Integer.compare(Integer.parseInt(an[i]),Integer.parseInt(bn[i]));if(c!=0)return c>0;}
        if(a.length==1&&b.length>1)return true;
        // Do not guess ordering between development suffixes with the same numeric release.
        return false;
    }
    public static byte[] request(){return new byte[]{1,0};}
    public static boolean isRequest(byte[] b){return b.length==2&&b[0]==1&&b[1]==0;}
    public static byte[] encode(Notice notice){
        try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(1);out.writeByte(1);out.writeUTF(notice.version());out.writeUTF(notice.url());return bytes.toByteArray();}catch(IOException e){throw new UncheckedIOException(e);}
    }
    public static Notice decode(byte[] b)throws IOException{
        if(b.length<6||b.length>4096)throw new IOException("Invalid update payload length");
        var in=new DataInputStream(new ByteArrayInputStream(b));if(in.readUnsignedByte()!=1||in.readUnsignedByte()!=1)throw new IOException("Invalid update packet");
        try{var n=new Notice(in.readUTF(),in.readUTF());if(in.available()!=0)throw new IOException("Trailing update data");return n;}catch(IllegalArgumentException e){throw new IOException(e);}
    }
}
