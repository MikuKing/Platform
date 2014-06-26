package com.csei.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.android.hdhe.nfc.NFCcmdManager;
import com.csei.entity.Employer;
import com.csei.entity.Listable;
import com.csei.entity.Tag;
import com.csei.exception.ArgumentException;
import com.csei.util.Tools;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class RFIDService extends Service {
	//��������
	private static final String AREA_PASSWORD = "FFFFFFFFFFFF";
	
	private NFCcmdManager cmdManager = null;
	//�㲥������
	private MyReceiver myReceiver; 	
	//������
	private String cardType = null;
	//���������activity
	private String activity = null;
	
	private String Tag = "RFIDService";  //Debug
	Timer searchCard = null;
	String cardID;
	

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	private String getSectorBlock(int sector,int block) {
		int value = sector*4 + block;
		Log.e("value", value + "");
		String value_str;
		if (value > 15) {
			value_str = Integer.toHexString(value);
		} else {
			value_str = "0" + Integer.toHexString(value);
		}
		return value_str;
	}

	
	
	
	//��ȡ���ݲ����ظ������
	private Handler handler = new Handler(){
		
		public void handleMessage(android.os.Message msg) {
			Listable listable;
			try {
				
				listable = readListable();
				Intent serviceIntent = new Intent();
				serviceIntent.setAction(activity);
				serviceIntent.putExtra("listable",listable);
				sendBroadcast(serviceIntent);
				
			} catch (ArgumentException e) {
				e.printStackTrace();
			}
		};
		
		private String readSector(int sector){
			boolean authFlag = authCard(sector, AREA_PASSWORD, cardID);
			if (authFlag) {
				String block = getSectorBlock(sector, 0);
				String data = readCard(block);
				return data;
			}			
			return null;
		}
		
		private int getListableLength(){
			String lenString = readSector(1);
			return Integer.parseInt(lenString);
		}
		
		private Listable readListable() throws ArgumentException{
			Listable result = null;
			int len = getListableLength();
			List<String> list = new ArrayList<String>();
			for(int i=1;i<=len;i++){
				list.add(readSector(i+1));
			}
			
			if(list.get(0).equals("0x01")){
				result = new Employer();
			}else{
				result = new Tag();
			}
			result.setByList(list);
			
			return result;
		}
		
		
		//��֤
		private boolean authCard(int sector, String password, String cardID){
			boolean flag = false;
			byte[] cardIDBytes = Tools.HexString2Bytes(cardID);
			int cardIDLen = cardIDBytes.length;
			byte[] passwordBytes = Tools.HexString2Bytes(password);
			
			flag = cmdManager.authMifare14443A(0, sector, passwordBytes, cardIDLen, cardIDBytes);
			return flag;
		}
		
		//������
		private String readCard(String block){
			String data = null;
			byte[] dataBytes = null;
			byte[] blockBytes = Tools.HexString2Bytes(block);
			dataBytes = cmdManager.readMifare14443A(blockBytes);
			if(dataBytes != null){
				data = new String(dataBytes);
				if(data.indexOf("\0")>0){
					data = data.substring(0, data.indexOf("\0"));
				}
				Log.i(Tag, "read card   " + data);
			}
			return data;
		}
		
		//д����
		private boolean writeCard(String block, String data){
			boolean flag = false;
			byte[] dataBytes = Tools.HexString2Bytes(data);
//			byte[] blocks = Tools.HexString2Bytes(block);
			int bolckInt = Integer.parseInt(block, 16);
			Log.i("", bolckInt + "");
			flag = cmdManager.writeMifare14443A(bolckInt, dataBytes);
			return flag;
		}
	};

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		Log.i(Tag, "service onCreate");
		cmdManager = NFCcmdManager.getNFCcmdManager(13, 115200, 0);  //�򿪴��ڣ�����13��������115200
		if(cmdManager != null){
			
			cmdManager.readerPowerOn();//�򿪵�Դ
			/*����ģ���Ƿ�������*/
			String version = cmdManager.getVersion();
			if(version != null){
				Log.i(Tag, "cmdManager version "+version);
			}
		}
		// ע��Broadcast Receiver�����ڹر�Service
		myReceiver = new MyReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction("com.csei.service.RFIDService");
		registerReceiver(myReceiver, filter);
		
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		Log.i(Tag, "onStartCommand");
		cardType = intent.getStringExtra("cardType");
		if (intent.getStringExtra("activity") != null) {
			activity = intent.getStringExtra("activity");
			Log.i(Tag, activity);
		}
		this.startSearch(cardType, 1);
		return super.onStartCommand(intent, flags, startId);
	}
	
	//Ѱ��,��ȡcardID
	private void startSearchCard(){
		searchCard = new Timer();
		searchCard.schedule(new TimerTask() {
			boolean init14443AFlag ;
			//14443AcardID
			byte[] cardID14443A = null;
//			byte[] b
			@Override
			public void run() {
				//Ѱ�����̣�14443A��ʼ��-->14443AѰ��-->14443Aȡ����ʼ��
				init14443AFlag = cmdManager.init_14443A();
				if(init14443AFlag){
					Log.i(Tag, "init14443A flag    " + init14443AFlag );
					// Ѱ��
					cardID14443A = cmdManager.inventory_14443A();
					if(cardID14443A != null ){
						
						//ȡ��14443A��ʼ��
						if(cmdManager.deInit_14443A()){
							//Mifare��ʼ��
							if(cmdManager.initMifare14443A()){
								cardID = Tools.Bytes2HexString(cardID14443A, cardID14443A.length);
								Log.i(Tag, "rfid car cardID " + cardID );
								Message msg = new Message();
								Bundle bundle = new Bundle();
								bundle.putString("cardID", cardID);
								msg.setData(bundle);
								handler.sendMessage(msg);
								Log.i(Tag, "startSearchCard   " + cardID );
								searchCard.cancel();
								return;
							}
						}
						
					}
					
				}
				
			}
		}, 10, 10);
	}
	
	private void startSearch(String cardtype, int count){
		if (cardType.equals("0x01")) {
			startSearchCard();
		} else if (cardType.equals("0x02")) {
			startSearchCard();
		} else {
			Log.e("cardType", cardType + " is not right!0x01|0x02");
			// ���ض��������ݸ�������
			Intent serviceIntent = new Intent();
			serviceIntent.setAction(activity);
			serviceIntent.putExtra("code", "1");
			serviceIntent.putExtra("result", "�����ʹ���");
			sendBroadcast(serviceIntent);
		}
	}
	
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		Log.i(Tag, "onDestroy");
		//ж�ع㲥ע��
		if(myReceiver != null){
			unregisterReceiver(myReceiver);
		}
		//�رմ���
		if(cmdManager != null){
			cmdManager.close(13);
			Log.i(Tag, "onDestroy close");
		}
		super.onDestroy();
		
	}
	
	/**
	 * �㲥������
	 * @author Jimmy Pang
	 */
	private class MyReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			String ac = intent.getStringExtra("activity");
			if (ac != null)
				Log.e("receive activity", ac);
			activity = ac; // ��ȡactivity
			//�رշ���
			if (intent.getBooleanExtra("stopflag", false)) {
				stopSelf(); // �յ�ֹͣ�����ź�
				Log.e("stop service", intent.getBooleanExtra("stopflag", false) + "");
			}
			//�ر�Ѱ��
			if (intent.getBooleanExtra("stopSearch", false)) {
				if(searchCard != null){
					searchCard.cancel();
				}
				Log.e("stop search", intent.getBooleanExtra("stopSearch", false) + "");
			}
		}

	}
}
