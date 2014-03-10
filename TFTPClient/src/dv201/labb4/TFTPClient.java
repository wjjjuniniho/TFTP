package dv201.labb4;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class TFTPClient {

	public static final int TFTPPORT = 0;
	public static final int BUFSIZE = 516;
	public static final short OP_RRQ = 1;
	public static final short OP_WRQ = 2;
	public static final short OP_DAT = 3;
	public static final short OP_ACK = 4;
	public static final short OP_ERR = 5;

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		// ServerIP ServerPort mode opcode(get/put) filename
		if (args.length != 5) {
			System.err
					.println("usage: ServerIP ServerPort mode opcode(get/put) filename");
			System.exit(1);
		}

		try {
			TFTPClient client = new TFTPClient();
			client.start(args);
		} catch (SocketException e) {
			e.printStackTrace();
		}

	}

	private void start(String[] args) throws SocketException {
		byte[] sendBuf = new byte[BUFSIZE];

		if (initialBufSuccess(args, sendBuf) == false) {
			System.err.println("Input error");
			System.exit(1);
		}
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(null);
			SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
			socket.bind(localBindPoint);
			// send RQ
			SocketAddress remoteBindPoint = new InetSocketAddress(args[0],
					Integer.valueOf(args[1]));
			DatagramPacket sendPacket = new DatagramPacket(sendBuf,
					sendBuf.length, remoteBindPoint);
			socket.send(sendPacket);

			//receive
		//	if(args[2].equals("octet")){
				if(args[3].equals("get")){
					RQHandle(socket, remoteBindPoint, args[4], args[2], null, OP_RRQ);
				}else if(args[3].equals("put")){
					RQHandle(socket, remoteBindPoint, args[4], args[2],sendPacket, OP_WRQ);
				}else{
					System.err.println("RQ input error!");
					System.exit(1);
				}
	//		}
			

		} catch (IOException e) {
			e.printStackTrace();
		}finally{
			socket.close();

		}
	}
	
	
	private void RQHandle(DatagramSocket socket,
			SocketAddress remoteBindPoint, String stringFile, String stringMod,DatagramPacket sendRQPacket,
			short opRrq){
		if(opRrq==OP_RRQ){
			RRQHandle(socket, remoteBindPoint, stringFile, stringMod);
		}else if(opRrq==OP_WRQ){
			WRQHandle(socket, remoteBindPoint, stringFile, stringMod,sendRQPacket);
		}else
			return;
	}
	
	
	private void WRQHandle(DatagramSocket socket,
			SocketAddress remoteBindPoint, String stringFile, String stringMod,DatagramPacket sendRQPacket){
		
		short block = 0;
		File fileRead = null;
		FileInputStream is = null;
		byte[] dataBuf = null;
		int byteRead = 0;

		try {
			fileRead = new File(stringFile);
			is = new FileInputStream(fileRead);
			dataBuf = new byte[BUFSIZE];

			remoteBindPoint=ClientWriteAndReadAck(socket,sendRQPacket,block++,true);
			while ((byteRead = is.read(dataBuf, 4, BUFSIZE - 4)) != -1) {
				DatagramPacket sendPacket = formPacket(OP_DAT, block, dataBuf,
						byteRead, remoteBindPoint);
				ClientWriteAndReadAck(socket, sendPacket, block,false);
				block++;
			}

		} catch (FileNotFoundException e) {
			System.err.println("File " + stringFile + " cannot be found!");
			// send Error message
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			System.err.println(e.getMessage());
			// send Error message
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
		
	}
	
	
		
	
	private SocketAddress ClientWriteAndReadAck(DatagramSocket sendSocket,
			DatagramPacket sendPacket, short block,boolean bRQAck) throws Exception {
		byte[] rcvAckBuf = null;
		SocketAddress rcvRemoteAddress=null;
		int base = 1000;
		int iTime = base;
		for (; iTime < 32 * base; iTime *= 2) {
			try {
				if(bRQAck){
					bRQAck=false;
				}else{
					sendSocket.send(sendPacket);
				}

				// timeout...
				sendSocket.setSoTimeout(iTime);
				// recevie ACK Packet
				rcvAckBuf = new byte[BUFSIZE];
				DatagramPacket rcvPacket = new DatagramPacket(rcvAckBuf,
						rcvAckBuf.length);
				sendSocket.receive(rcvPacket);
				short ACK = ParseRQ(rcvAckBuf, null, null);
				short rcvBlock = ParseBLK(rcvAckBuf);
				if (ACK == OP_ACK) {
					if (block == rcvBlock) {
						 System.out.println("ACK block " + block +
						 " received");
						 rcvRemoteAddress=rcvPacket.getSocketAddress();
						break;
					} else {
						System.err
								.println("Exception:wrong packet received!Retransmission!");
						continue;
					}
				}else if(ACK == OP_ERR){
					StringBuffer errorMsg=new StringBuffer();
					short errorCode=ParseERR(rcvAckBuf, errorMsg);
					throw new Exception("Error code:"+errorCode+" Error Message:"+errorMsg.toString());
				}

			} catch (SocketTimeoutException e) {
				System.err.println("Time expire!Retransmission block:" + block);
				continue;
			} catch (SocketException e) {

			} catch (IOException e) {

			}
		}
		if (iTime == 32 * base) {
			throw new Exception("Retransmission maximum reached for block"
					+ block + "!");
		}else{
			return rcvRemoteAddress;
		}
		
	}

	
	
	
	
	private void RRQHandle(DatagramSocket socket,
			SocketAddress remoteBindPoint, String stringFile, String stringMod){
		// get:receive DATA
		short block = 1;
		boolean finFlag=false;
		FileOutputStream out=null;
		File fileWrite=null;
		fileWrite=new File(stringFile);
		try {
			out=new FileOutputStream(fileWrite);
			while(!finFlag){
				byte[] rcvDataBuf = new byte[BUFSIZE];
				byte[] fileContentBuf = new byte[BUFSIZE - 4];
				byte[] sendAckBuf=new byte[BUFSIZE];
				DatagramPacket rcvPacket = new DatagramPacket(rcvDataBuf,
						rcvDataBuf.length);
				socket.receive(rcvPacket);
				// Parse packet
				short opcode = ParseRQ(rcvDataBuf, fileContentBuf, null);
				short rcvBlock= ParseBLK(rcvDataBuf);
				if(opcode==OP_DAT ){
					if(rcvBlock!=block){
						continue;
					}
					finFlag=HandleDAT(fileContentBuf,rcvPacket.getLength()-4,out);
					System.out.println( "length:"+(rcvPacket.getLength()-4)+" ACK block send: "+block);
					DatagramPacket sendAckPacket=formPacket(OP_ACK, block++, sendAckBuf, 0,rcvPacket.getSocketAddress());

					socket.send(sendAckPacket);
					
					
				}else if(opcode==OP_ERR){
					//...error handling
					StringBuffer errorMsg=new StringBuffer();
					short errorCode=ParseERR(rcvDataBuf, errorMsg);
					throw new Exception("Error code:"+errorCode+" Error Message:"+errorMsg.toString());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.err.println(e.getMessage());
		}finally{
			try{
				out.close();
			}catch(IOException e){
			}
		}

	
	}
	
	
	
	private DatagramPacket formPacket(short opcode, short block, byte[] buf,int byteRead,
			SocketAddress remoteBindPoint) {

		if (opcode == OP_DAT) {
			return formDataPacket(opcode, block, buf, byteRead, remoteBindPoint);
		}else if(opcode == OP_ACK){
			return formAckPacket(opcode, block,buf,remoteBindPoint);
		}
		return null;
	}
	
	private DatagramPacket formAckPacket(short opcode,short block,byte[] buf,SocketAddress remoteBindPoint){
		ByteBuffer wrap=ByteBuffer.wrap(buf);
		wrap.putShort(opcode);
		wrap.putShort(block);
		try{
			DatagramPacket sendPacket = new DatagramPacket(buf, 4,
				remoteBindPoint);
			return sendPacket;
			
		}catch(SocketException e){
			e.printStackTrace();
			return null;
		}
	}
	
	
	private DatagramPacket formDataPacket(short opcode, short block, byte[] buf,int byteRead,
			SocketAddress remoteBindPoint) {
		ByteBuffer wrap=ByteBuffer.wrap(buf);
		wrap.putShort(opcode);
		wrap.putShort(block);
		try{
			DatagramPacket sendPacket = new DatagramPacket(buf, byteRead+4,
				remoteBindPoint);
			return sendPacket;
			
		}catch(SocketException e){
			e.printStackTrace();
			return null;
		}
	}
	

	private boolean HandleDAT(byte[] fileContentBuf,int length,FileOutputStream out){
		
		try{
			out.write(fileContentBuf, 0, length);
		}catch(IOException e){
			e.printStackTrace();
		}
		if(length ==(BUFSIZE-4) ){
			return false;
		}//end of file transmission
		else if(length < (BUFSIZE-4) ){
			return true;
		}
		else
			return true;

	}
	
	private short ParseBLK(byte[] buf){
		ByteBuffer rcvWrap=ByteBuffer.wrap(buf);
		short RQ=rcvWrap.getShort();
		if(RQ==OP_DAT || RQ==OP_ACK){
			return rcvWrap.getShort();
		}else{
			return -1;
		}
	}
	
	
	private short ParseERR(byte[] buf,StringBuffer errorMsg){
		ByteBuffer rcvWrap = ByteBuffer.wrap(buf);
		int endErrStringIndex=-2;
		short RQ = rcvWrap.getShort();
		short errorCode=rcvWrap.getShort();
		if(RQ == OP_ERR){
			endErrStringIndex=getStringEnd(buf, 2);
			if(endErrStringIndex<4){
				endErrStringIndex=getStringEnd(buf, 3);
			}
			errorMsg.append(new String(buf,4,endErrStringIndex-4));
			return errorCode;
		}else{
			return -1;
		}
	}
	
	
	private  short ParseRQ(byte[] buf, byte[] fileContentBuf,
			 StringBuffer requestedFile) {
		int endFileStringIndex = -2;
		ByteBuffer rcvWrap = ByteBuffer.wrap(buf);
		short RQ = rcvWrap.getShort();
		if (RQ == OP_RRQ || RQ == OP_WRQ) {
			endFileStringIndex = getStringEnd(buf, 1);
			requestedFile.append(new String(buf, 2, endFileStringIndex - 2));
		} else if (RQ == OP_ERR) {
			// ...error code
		} else if (RQ == OP_ACK) {
			// parse block

		} else if (RQ == OP_DAT) {
			rcvWrap.position(4);
			rcvWrap.get(fileContentBuf, 0, buf.length-4);
			// parse block && data
		} else {
			return -1;
		}
		if (endFileStringIndex == -1) {
			return -1;
		}
		return RQ;
	}

	private int getStringEnd(byte[] buf, int index) {
		int nilCount = 0;
		for (int i = 0; i < buf.length; i++) {
			if (buf[i] == 0) {
				if ((nilCount++) == index) {
					return i;
				}
			}
		}
		System.err.println("Packet format error");
		return -1;
	}

	private boolean initialBufSuccess(String[] args, byte[] buf) {
		ByteBuffer sendWrap = ByteBuffer.wrap(buf);
		short opcode = 0;
		byte StringEnd = 0;
		byte[] filename = args[4].getBytes();
		byte[] mode = args[2].getBytes();

		if (args[3].equals("get")) {
			opcode = OP_RRQ;
		} else if (args[3].equals("put")) {
			opcode = OP_WRQ;
		} else {
			return false;
		}

		sendWrap.putShort(opcode);
		sendWrap.put(filename);
		sendWrap.put(StringEnd);
		sendWrap.put(mode);
		sendWrap.put(StringEnd);

		return true;
	}

}
