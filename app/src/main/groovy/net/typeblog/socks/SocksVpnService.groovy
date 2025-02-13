package net.typeblog.socks

import android.app.Notification
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.util.Log
import android.os.Build
import net.typeblog.socks.R
import net.typeblog.socks.IVpnService
import net.typeblog.socks.System
import net.typeblog.socks.util.Routes
import net.typeblog.socks.util.Utility
import static net.typeblog.socks.util.Constants.*
import static net.typeblog.socks.BuildConfig.DEBUG
import android.support.annotation.RequiresApi
import 	android.app.NotificationManager
import 	android.app.NotificationChannel
import android.graphics.Color

public class SocksVpnService extends VpnService {
	private static final String TAG = 'SocksVpnService'
	
	private ParcelFileDescriptor mInterface
	private boolean mRunning = false


	@Override int onStartCommand(Intent intent, int flags, int startId) {
		
		if (DEBUG) {
			Log.d(TAG, "starting")
		}
		
		if (!intent) {
			return 0
		}
		
		if (mRunning) {
			return 0
		}

		final String name = intent.getStringExtra(INTENT_NAME);
		Log.d(TAG, "name :  "+name)
		final String server = intent.getStringExtra(INTENT_SERVER);
		Log.d(TAG, "server :  "+server)
		final int port = intent.getIntExtra(INTENT_PORT, 1080);
		Log.d(TAG, "port :  "+port)
		final String username = intent.getStringExtra(INTENT_USERNAME);
		final String passwd = intent.getStringExtra(INTENT_PASSWORD);
		final String route = intent.getStringExtra(INTENT_ROUTE);
		Log.d(TAG, "route :  "+route)
		final String dns = intent.getStringExtra(INTENT_DNS);
		Log.d(TAG, "dns :  "+dns)
		final int dnsPort = intent.getIntExtra(INTENT_DNS_PORT, 53);
		Log.d(TAG, "dnsPort :  "+dnsPort)
		final boolean perApp = intent.getBooleanExtra(INTENT_PER_APP, false);
		Log.d(TAG, "perApp :  "+perApp)
		final boolean appBypass = intent.getBooleanExtra(INTENT_APP_BYPASS, false);
		final String[] appList = intent.getStringArrayExtra(INTENT_APP_LIST);
		final boolean ipv6 = intent.getBooleanExtra(INTENT_IPV6_PROXY, false);
		Log.d(TAG, "ipv6 :  "+ipv6)
		final String udpgw = intent.getStringExtra(INTENT_UDP_GW);
		Log.d(TAG, "udpgw :  "+udpgw)

		/*****************adding by Jack****************/
		File proxy = new File("${DIR}/proxy.txt")

		if (proxy.exists()==false) {
			Log.d(TAG, "proxy.txt NOT existed")
		}else{
			def lines = proxy.readLines()
			String proxyline=null
			lines.each { String line ->
				if(line!=null && line.length()>3){
					proxyline=line
					return true
				}
			}
			if(proxyline!=null && proxyline.contains(":")){
				def all=proxyline.split(":")
				final  String server0=all[0]
				final  int port0 = Integer.parseInt(all[1])
				// Create the notification
				startForeground(R.drawable.ic_launcher,
						new Notification.Builder(this).with {
							contentTitle = getString(R.string.notify_title)
							contentText = String.format(getString(R.string.notify_msg), "SocksDroid")
							priority = Notification.PRIORITY_MIN
							smallIcon = android.R.color.transparent
							build()
						})

				// Create an fd.
				configure(name, route, perApp, appBypass, appList, ipv6)

				if (DEBUG)
					Log.d(TAG, "fd: ${mInterface.fd}")

				if (mInterface)
					start(mInterface.getFd(), server0, port0, username, passwd, dns, dnsPort, ipv6, udpgw)

				START_STICKY
				return
			}else{
				Log.d(TAG, "proxy.txt NOT contains proxy information!")
			}
		}

		// Create the notification
		def channelId =""
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			channelId =createNotificationChannel("my_service", name)
		} else {
			// If earlier version channel ID is not used
			// https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
			channelId =""
		}

		def notificationBuilder = NotificationCompat.Builder(this, channelId )
		def notification = notificationBuilder.setOngoing(true)
				.setSmallIcon(android.R.color.transparent)
				.setPriority(PRIORITY_MIN)
				.setCategory(Notification.CATEGORY_SERVICE)
				.build()
		startForeground(101, notification)
/**
 *      https://stackoverflow.com/questions/47531742/startforeground-fail-after-upgrade-to-android-8-1
 *
		startForeground(R.drawable.ic_launcher,
			new Notification.Builder(this).with {
				contentTitle = getString(R.string.notify_title)
				contentText = String.format(getString(R.string.notify_msg), name)
				priority = Notification.PRIORITY_MIN
				smallIcon = android.R.color.transparent
				build()
			})
**/
		// Create an fd.
		configure(name, route, perApp, appBypass, appList, ipv6)
		
		if (DEBUG)
			Log.d(TAG, "fd: ${mInterface.fd}")
		
		if (mInterface)
			start(mInterface.getFd(), server, port, username, passwd, dns, dnsPort, ipv6, udpgw)
		
		START_STICKY
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private String createNotificationChannel(String channelId,String channelName){
		def chan = new  NotificationChannel(channelId,
				channelName, NotificationManager.IMPORTANCE_NONE)
		chan.lightColor = Color.BLUE
		chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
		def NotificationManager service = getSystemService(Context.NOTIFICATION_SERVICE)
		service.createNotificationChannel(chan)
		return channelId
	}

	@Override void onRevoke() {
		super.onRevoke()
		stopMe()
	}

	@Override IBinder onBind(Intent intent) {
		return new IVpnService.Stub() {
			@Override boolean isRunning() {
				mRunning
			}
			
			@Override void stop() {
				stopMe()
			}
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		stopMe();
	}
	
	private void stopMe() {
		stopForeground(true)
		
		Utility.killPidFile("${DIR}/tun2socks.pid")
		Utility.killPidFile("${DIR}/pdnsd.pid")
		
		try {
			System.jniclose(mInterface.fd)
			mInterface.close()
		} catch (any) {
		}
		
		stopSelf()
	}
	
	private void configure(String name, String route, boolean perApp, boolean bypass, String[] apps, boolean ipv6) {
		VpnService.Builder b = new VpnService.Builder(this)
		b.with {
			mtu = 1500
			session = name
			addAddress "26.26.26.1", 24
			Log.d(TAG, "addAddress -> 26.26.26.1")
			addDnsServer "8.8.8.8"
			Log.d(TAG, "addDnsServer -> 8.8.8.8")

			if (ipv6) {
				// Route all IPv6 traffic
				addAddress "fdfe:dcba:9876::1", 126
				addRoute "::", 0
			}
			
			Routes.addRoutes(this, b, route);
			
			// Add the default DNS
			// Note that this DNS is just a stub.
			// Actual DNS requests will be redirected through pdnsd.
			addRoute "8.8.8.8", 32
			Log.d(TAG, "8.8.8.8")
			// Adding by Jack
			addRoute "192.168.0.1", 24
			Log.d(TAG, "192.168.0.1")
			addRoute "192.168.1.1", 24
			Log.d(TAG, "192.168.1.1")
			addRoute "192.168.2.1", 24
			Log.d(TAG, "192.168.2.1")
			//Team viewer
			addRoute "13.112.89.178" ,32
			addRoute "46.163.100.220" ,32
			addRoute "108.59.5.129" ,32
			addRoute "108.59.5.130" ,32
			addRoute "95.211.37.197" ,32
			addRoute "95.211.37.198" ,32
			addRoute "188.120.239.102" ,32
			addRoute "188.93.226.130" ,32
			addRoute "46.105.99.93" ,32
			addRoute "178.77.120.6" ,32
			addRoute "87.230.74.43" ,32
			addRoute "87.230.74.44" ,32
			addRoute "13.112.89.178" ,32
			addRoute "13.112.89.178" ,32
			addRoute "13.112.89.178" ,32
			addRoute "13.112.89.178" ,32
			addRoute "13.112.89.178" ,32
			addRoute "13.112.89.178" ,32
			addRoute "13.112.89.178" ,32
			addRoute "13.112.89.178" ,32
			addRoute "13.112.89.178" ,32
			addRoute "13.112.89.178" ,32

		}
		
		// Do app routing
		if (!perApp) {
			// Just bypass myself
			try {
				b.addDisallowedApplication("net.typeblog.socks");
			} catch (Exception e) {
				
			}
		} else {
			if (bypass) {
				// First, bypass myself
				try {
					b.addDisallowedApplication("net.typeblog.socks");
				} catch (Exception e) {

				}
				
				apps.each { p ->
					if (TextUtils.isEmpty(p))
						return
					
					try {
						b.addDisallowedApplication(p.trim())
					} catch (any) {
						
					}
				}
			} else {
				apps.each { p ->
					if (TextUtils.isEmpty(p) || p.trim().equals("net.typeblog.socks")) {
						return
					}
					
					try {
						b.addAllowedApplication(p.trim())
					} catch (any) {
						
					}
				}
			}
		}
			
		mInterface = b.establish()
	}

	private void start(int fd, String server, int port, String user, String passwd, String dns, int dnsPort, boolean ipv6, String udpgw) {
		// Start DNS daemon first
		Utility.makePdnsdConf(this, dns, dnsPort)
		
		Utility.exec("${DIR}/pdnsd -c ${DIR}/pdnsd.conf")
		
		String command = """${DIR}/tun2socks --netif-ipaddr 26.26.26.2
			--netif-netmask 255.255.255.0
			--socks-server-addr ${server}:${port}
			--tunfd ${fd}
			--tunmtu 1500
			--loglevel 3
			--pid ${DIR}/tun2socks.pid
			--dnsgw 26.26.26.1:8091
			${ -> ipv6 ? " --netif-ip6addr fdfe:dcba:9876::2" : "" }
			${ -> udpgw ? " --udpgw-remote-server-addr $udpgw" : "" }
			${ -> user ? " --username $user --password $passwd" : "" }
			""".stripIndent().replace("\n", "")
		
		if (DEBUG) {
			Log.d(TAG, command)
		}
		
		if (Utility.exec(command) != 0) {
			stopMe()
			return
		}
		
		// Try to send the Fd through socket.
		int i = 5
		while (i-- > 0) {
			if (System.sendfd(fd) != -1) {
				mRunning = true
				return
			}
			
			try {
				Thread.sleep(1000 * i)
			} catch (any) {
				
			}
		}
		
		// Should not get here. Must be a failure.
		stopMe()
	}
}
