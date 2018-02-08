package com.okamipride.scanlanip.scan;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Created by WTZ on 2018/2/7.
 */

public class NetUtil {
    private static final String TAG = "NetUtil";

    public static Map<String, String> getNetworkInfo(Context context) {
        String mac = "";
        String ip = "";
        String mask = "";
        String gateway = "";
        String dns1 = "";
        String dns2 = "";
        String ssid = "";
        String bssid = "";
        Map<String, String> results = new HashMap<String, String>();

        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo connectedInfo = null;
        if (cm != null) {
            NetworkInfo[] infos = cm.getAllNetworkInfo();
            for (NetworkInfo ni : infos) {
                if (ni.getState() == NetworkInfo.State.CONNECTED) {
                    Log.d(TAG, "find connected info, type is " + ni.getTypeName());
                    connectedInfo = ni;
                    break;
                }
            }
        }

        if (connectedInfo != null) {
            // 已连接
            switch (connectedInfo.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                    WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo != null) {
                        mac = wifiInfo.getMacAddress();
                        ip = int2ip(wifiInfo.getIpAddress());
                        ssid = wifiInfo.getSSID();
                        bssid = wifiInfo.getBSSID();
                        DhcpInfo di = wifiManager.getDhcpInfo();
                        if (di != null) {
                            gateway = int2ip(di.gateway);
                            mask = int2ip(di.netmask);
                            dns1 = int2ip(di.dns1);
                            dns2 = int2ip(di.dns2);
                        }
                    }

                    if (TextUtils.isEmpty(mac) || mac.contains("00:00:00:00")) {
                        mac = readDevMacFromWlan0();
                    }
                    if (TextUtils.isEmpty(ip) || !isIpString(ip)) {
                        ip = androidGetProp("dhcp.wlan0.ipaddress", "");
                    }
                    if (TextUtils.isEmpty(mask)) {
                        mask = androidGetProp("dhcp.wlan0.mask", "");
                    }
                    if (TextUtils.isEmpty(gateway)) {
                        gateway = androidGetProp("dhcp.wlan0.gateway", "");
                    }
                    if (TextUtils.isEmpty(dns1)) {
                        dns1 = androidGetProp("dhcp.wlan0.dns1", "");
                    }
                    if (TextUtils.isEmpty(dns2)) {
                        dns2 = androidGetProp("dhcp.wlan0.dns2", "");
                    }
                    break;
                case ConnectivityManager.TYPE_ETHERNET:
                    try {
                        for (Enumeration<NetworkInterface> en = NetworkInterface
                                .getNetworkInterfaces(); en.hasMoreElements(); ) {
                            NetworkInterface netInterface = en.nextElement();
                            List<InterfaceAddress> mList = netInterface.getInterfaceAddresses();
                            for (InterfaceAddress interfaceAddress : mList) {
                                InetAddress inetAddress = interfaceAddress.getAddress();
                                if (!inetAddress.isLoopbackAddress()) {
                                    String hostAddress = inetAddress.getHostAddress();
                                    Log.d(TAG, "inetAddress.getHostAddress = " + hostAddress);
                                    if (!hostAddress.contains("::")) {
                                        mac = getMacByInetAddress(inetAddress);
                                        ip = hostAddress;
                                        mask = calcMaskByPrefixLength(interfaceAddress
                                                .getNetworkPrefixLength());
                                    }
                                }
                            }
                        }

                        Field cmServiceField = Class.forName(ConnectivityManager.class.getName())
                                .getDeclaredField("mService");
                        cmServiceField.setAccessible(true);
                        // connectivitymanager.mService
                        Object cmService = cmServiceField.get(cm);
                        // get IConnectivityManager class
                        Class cmServiceClass = Class.forName(cmService.getClass().getName());
                        Method methodGetLinkp = cmServiceClass.getDeclaredMethod("getLinkProperties",
                                new Class[]{int.class});
                        methodGetLinkp.setAccessible(true);
                        Object linkProperties = methodGetLinkp.invoke(cmService, ConnectivityManager.TYPE_ETHERNET);

                        Class<?> classLinkp = Class.forName("android.net.LinkProperties");
                        Method methodGetRoutes = classLinkp.getDeclaredMethod("getRoutes");
                        Method methodGetDnses = classLinkp.getDeclaredMethod("getDnses");
                        Collection<RouteInfo> routeInfos = (Collection<RouteInfo>) methodGetRoutes.invoke(linkProperties);
                        Collection<InetAddress> inetAddresses = (Collection<InetAddress>) methodGetDnses.invoke(linkProperties);

                        String routeInfoString = routeInfos.toString();
                        if (routeInfoString.contains(">")) {
                            gateway = routeInfoString.substring(
                                    routeInfoString.lastIndexOf('>') + 2,
                                    routeInfoString.length() - 1);
                            Log.d(TAG, "get gateway form routeInfoString: " + gateway);
                        }

                        String inetAddressString = inetAddresses.toString();
                        if (inetAddressString.contains(",")) {
                            dns1 = inetAddressString.substring(2,
                                    inetAddressString.lastIndexOf(","));
                            dns2 = inetAddressString.substring(
                                    inetAddressString.lastIndexOf(",") + 3,
                                    inetAddressString.length() - 1);
                            Log.d(TAG, "get dns form inetAddressString: " + dns1 + ", " + dns2);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (TextUtils.isEmpty(mac)) {
                        mac = readDevMacFromEth0();
                    }
                    if (TextUtils.isEmpty(ip) || !isIpString(ip)) {
                        ip = androidGetProp("dhcp.eth0.ipaddress", "");
                    }
                    if (TextUtils.isEmpty(mask)) {
                        mask = androidGetProp("dhcp.eth0.mask", "");
                    }
                    if (TextUtils.isEmpty(gateway)) {
                        gateway = androidGetProp("dhcp.eth0.gateway", "");
                        if (TextUtils.isEmpty(gateway)) {
                            gateway = getEth0GatewayByCmd();
                        }
                    }
                    if (TextUtils.isEmpty(dns1)) {
                        dns1 = androidGetProp("dhcp.eth0.dns1", "");
                    }
                    if (TextUtils.isEmpty(dns2)) {
                        dns2 = androidGetProp("dhcp.eth0.dns2", "");
                    }

                    break;
                case ConnectivityManager.TYPE_MOBILE:
                    // TODO: 2017/9/20
                    break;
                default:
                    break;
            }
        }

        results.put("mac", mac);
        results.put("ip", ip);
        results.put("mask", mask);
        results.put("gateway", gateway);
        results.put("dns1", dns1);
        results.put("dns2", dns2);
        results.put("ssid", ssid);
        results.put("bssid", bssid);
        return results;
    }

    private static String getMacByInetAddress(InetAddress inetAddress) {
        StringBuffer buffer = null;
        byte[] bytes = null;

        try {
            bytes = NetworkInterface.getByInetAddress(inetAddress)
                    .getHardwareAddress();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        if (bytes != null && bytes.length > 0) {
            buffer = new StringBuffer();
            for (int i = 0; i < bytes.length; i++) {
                if (i != 0) {
                    buffer.append(':');
                }
                String str = Integer.toHexString(bytes[i] & 0xFF);
                buffer.append(str.length() == 1 ? 0 + str : str);
            }
        }

        return (buffer != null) ? buffer.toString().toLowerCase() : null;
    }

    private static String getEth0GatewayByCmd() {
        String gateWay = null;
        String prefix = "default via ";
        String suffix = " dev eth0";
        String cmd = "ip route show | grep \"default via\" | grep \"dev eth0\"";
        ShellUtils.CommandResult ret = ShellUtils.execCommand(cmd, false);
        Log.d(TAG, ret.toString());
        if (!TextUtils.isEmpty(ret.successMsg)) {
            int start = ret.successMsg.indexOf(prefix);
            int end = ret.successMsg.indexOf(suffix);
            if (start != -1 && end != -1) {
                start = start + prefix.length();
                gateWay = ret.successMsg.substring(start, end);
            }
        }
        return gateWay;
    }

    private static String calcMaskByPrefixLength(int length) {
        int mask = -1 << (32 - length);
        int partsNum = 4;
        int bitsOfPart = 8;
        int maskParts[] = new int[partsNum];
        int selector = 0x000000ff;


        for (int i = 0; i < maskParts.length; i++) {
            int pos = maskParts.length - 1 - i;
            maskParts[pos] = (mask >> (i * bitsOfPart)) & selector;
        }


        String result = "";
        result = result + maskParts[0];
        for (int i = 1; i < maskParts.length; i++) {
            result = result + "." + maskParts[i];
        }
        Log.d(TAG, "calcMaskByPrefixLength: " + result);
        return result;
    }

    private static String int2ip(long ip) {
        StringBuffer sb = new StringBuffer();
        sb.append(String.valueOf((int) (ip & 0xff)));
        sb.append('.');
        sb.append(String.valueOf((int) ((ip >> 8) & 0xff)));
        sb.append('.');
        sb.append(String.valueOf((int) ((ip >> 16) & 0xff)));
        sb.append('.');
        sb.append(String.valueOf((int) ((ip >> 24) & 0xff)));
        return sb.toString();
    }

    public static String androidGetProp(String key, String defaultValue) {
        String value = defaultValue;
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            value = (String) (get.invoke(c, key, ""));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "get property, " + key + " = " + value);
        return value;
    }

    private static boolean isIpString(String target) {
        String regex = "^(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])\\."
                + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\."
                + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\."
                + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)$";
        return isMatch(regex, target);
    }

    private static boolean isMatch(String regex, String target) {
        if (target == null || target.trim().equals("")) {
            return false;
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(target);
        return matcher.matches();
    }

    private static String readDevMacFromEth0() {
        final String path = "/sys/class/net/eth0/address";
        return readDevMac(path);
    }

    private static String readDevMacFromWlan0() {
        final String path = "/sys/class/net/wlan0/address";
        return readDevMac(path);
    }

    private static String readDevMac(final String path) {

        FileInputStream fis = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        String devMac = "";
        try {
            fis = new FileInputStream(new File(path));
            isr = new InputStreamReader(fis);
            br = new BufferedReader(isr);
            StringBuffer buffer = new StringBuffer();
            buffer.append(br.readLine());
            devMac = buffer.toString().trim();
            Log.d(TAG, "read mac from " + path + "-" + devMac);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
                if (isr != null) {
                    isr.close();
                }
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                // just ignore
                e.printStackTrace();
            }
        }
        return devMac;
    }

    public static Map<String, String> readArp() {
        BufferedReader br = null;
        Map<String, String> devices = null;
        try {
            br = new BufferedReader(new FileReader("/proc/net/arp"));
            String line = "";
            String ip = "";
            String mac = "";
            devices = new HashMap<String, String>();

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() < 63) {
                    continue;
                }
                if (line.toUpperCase(Locale.US).contains("IP")) {
                    continue;
                }
                if (line.contains("00:00:00:00:00:00")) {
                    continue;
                }

                ip = line.substring(0, 17).trim();
                mac = line.substring(41, 63).trim();
                devices.put(ip, mac);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return devices;
    }
}
