package net.screenfreeze.deskcon;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import android.content.Context;
import android.os.Build;
import android.util.Log;

public class Connection {
	
	public static SSLContext initSSLContext(Context context) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException, KeyManagementException {
		// load the keystore
		InputStream keyStoreStream;
		try {
			keyStoreStream = context.openFileInput("devicekeystore.bks");
		} catch (FileNotFoundException e1) {
			return null;
		}
		KeyStore MyKeyStore = KeyStore.getInstance("BKS");
		MyKeyStore.load(keyStoreStream, "android".toCharArray());
//		Enumeration<String> aliases = MyKeyStore.aliases();
//		while(aliases.hasMoreElements()) {
//			System.out.println(aliases.nextElement());
//		}
		
		// initialize trust manager factory with the read truststore
	    TrustManagerFactory trustManagerFactory = null;
	    trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(MyKeyStore);
		TrustManager[] tm = trustManagerFactory.getTrustManagers();
		
		// init KeyManagerFactory
		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(MyKeyStore, "passwd".toCharArray());
		KeyManager[] km = keyManagerFactory.getKeyManagers();
		
		
		// Set SSL Context
		SSLContext sslcontext;
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ) {
			sslcontext = SSLContext.getInstance("TLSv1.2");
		}
		else {
			sslcontext = SSLContext.getInstance("TLSv1");
		}
		
		sslcontext.init(km, tm, new SecureRandom());
		
		return sslcontext;
	}
	
	public static SSLSocket createSSLSocket(Context context, String host, int port) throws UnknownHostException, IOException {
		// init SSL Context
		SSLContext sslcontext = null;
		try {
			sslcontext = initSSLContext(context);
		} catch (Exception e) {
			e.printStackTrace();
		}		
		
		// make secure Connection
	    SSLSocketFactory factory = (SSLSocketFactory) sslcontext.getSocketFactory();
	    SSLSocket sslsocket = (SSLSocket) factory.createSocket();
	    sslsocket.setUseClientMode(true);
	    sslsocket.connect(new InetSocketAddress(host, port), 500);
	    
	    if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ) {
	    	sslsocket.setEnabledProtocols(new String[] {"TLSv1","TLSv1.1","TLSv1.2"});
	    }
	    else {
	    	sslsocket.setEnabledProtocols(new String[] {"TLSv1"});
	    }

	    Log.d("Connection: ", "using Protocol "+sslsocket.getSession().getProtocol());
	    Log.d("Connection: ", "Session valid  "+sslsocket.getSession().isValid());
		
		return sslsocket;
	}
	
	public static SSLServerSocket createSSLServerSocket(Context context, int port) throws IOException {
		// get ssl context
		SSLContext sslcontext = null;
		try {
			sslcontext = initSSLContext(context);
		} catch (Exception e) {
			e.printStackTrace();
		}	
		
		// make secure Connection
	    SSLServerSocketFactory factory = (SSLServerSocketFactory) sslcontext.getServerSocketFactory();
	    SSLServerSocket sslServerSocket = (SSLServerSocket) factory.createServerSocket(port);
	    if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ) {
	    	sslServerSocket.setEnabledProtocols(new String[] {"TLSv1","TLSv1.1","TLSv1.2"});
	    }
	    else {
	    	sslServerSocket.setEnabledProtocols(new String[] {"TLSv1"});
	    }
	    sslServerSocket.setReuseAddress(true);
	    
	    return sslServerSocket;
	}
}
