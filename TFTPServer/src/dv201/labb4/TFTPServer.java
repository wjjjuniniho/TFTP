package dv201.labb4;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.AccessControlException;

public class TFTPServer {
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "c:\\";
	public static final String WRITEDIR = "d:\\";
	public static final String MOD_OCTET = "octet";
	public static final short OP_RRQ = 1;
	public static final short OP_WRQ = 2;
	public static final short OP_DAT = 3;
	public static final short OP_ACK = 4;
	public static final short OP_ERR = 5;

	public static void main(String[] args) {
		if (args.length > 0) {
			System.err.printf("usage: java %s\n", TFTPServer.class
					.getCanonicalName());
			System.exit(1);
		}
		try {
			TFTPServer server = new TFTPServer();
			server.start();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	private void start() throws SocketException {
		byte[] buf = new byte[BUFSIZE];

		/* Create socket */
		DatagramSocket socket = new DatagramSocket(null);

		/* Create local bind point */
		SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

		while (true) { /* Loop to handle various requests */

			final InetSocketAddress clientAddress = receiveFrom(socket, buf);
			if (clientAddress == null) /*
										 * If clientAddress is null, an error
										 * occurred in receiveFrom()
										 */
				continue;

			final StringBuffer requestedFile = new StringBuffer();
			final StringBuffer requestedMode = ParseMOD(buf);
			final short reqtype;
			try {
				reqtype = ParseRQ(buf, null, requestedFile);
			} catch (TFTPIllegalOperation e) {
				// send error msg
				System.err.println(e.getMessage());
				byte[] errBuf = new byte[BUFSIZE];
				DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
						(short) 4, errBuf, 0, e.getMessage(), clientAddress);
				try {
					socket.send(sendErrPacket);
				} catch (IOException e1) {
				}
				continue;
			}

			System.out
					.printf(
							"%s request for %s from %s using port %d with transmission mode %s\n",
							(reqtype == OP_RRQ) ? "Read" : "Write",
							requestedFile, clientAddress.getHostName(),
							clientAddress.getPort(), requestedMode);

			new Thread() {
				public void run() {
					DatagramSocket sendSocket = null;
					try {
						sendSocket = new DatagramSocket(0);

						System.out.println("Local transmission port:"
								+ sendSocket.getLocalPort());

						if (requestedMode.toString().equals(MOD_OCTET)) {
							if (reqtype == OP_RRQ) { /* read request */
								requestedFile.insert(0, READDIR);
								HandleRQ(sendSocket, clientAddress,
										requestedFile.toString(), requestedMode
												.toString(), OP_RRQ);
							} else if (reqtype == OP_WRQ) { /* write request */
								requestedFile.insert(0, WRITEDIR);
								HandleRQ(sendSocket, clientAddress,
										requestedFile.toString(), requestedMode
												.toString(), OP_WRQ);
							} else {
								System.err.println("Illegal TFTP operation");
							}
						}else{	//other transfer mode:not support
							System.err.println("Fail to handle mode '"+requestedMode.toString()+"'");
							byte[] buf = new byte[BUFSIZE];
							DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
									(short) 4, buf, 0, "Illegal TFTP operation", clientAddress);
							try {
								sendSocket.send(sendErrPacket);
							} catch (IOException e1) {
							}
						}

					} catch (SocketException e) {
						e.printStackTrace();
					} finally {
						sendSocket.close();
					}
				}
			}.start();
		}
	}

	/**
	 * Reads the first block of data, i.e., the request for action (read or
	 * write).
	 * 
	 * @param socket
	 *            socket to read from
	 * @param buf
	 *            where to store the read data
	 * @return the Internet socket address of the client
	 */

	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
		DatagramPacket rcvPacket = new DatagramPacket(buf, buf.length);
		try {
			socket.receive(rcvPacket);
		} catch (IOException e) {
			System.err.println("Socket error!");
			return null;
		}
		buf = rcvPacket.getData();
		return (InetSocketAddress) rcvPacket.getSocketAddress();
	}

	private short ParseBLK(byte[] buf) {
		ByteBuffer rcvWrap = ByteBuffer.wrap(buf);
		short RQ = rcvWrap.getShort();
		if (RQ == OP_DAT || RQ == OP_ACK) {
			return rcvWrap.getShort();
		} else {
			return -1;
		}
	}

	private short ParseERR(byte[] buf, StringBuffer errorMsg) {
		ByteBuffer rcvWrap = ByteBuffer.wrap(buf);
		int endErrStringIndex = -2;
		short RQ = rcvWrap.getShort();
		short errorCode = rcvWrap.getShort();
		if (RQ == OP_ERR) {
			endErrStringIndex = getStringEnd(buf, 2);
			if (endErrStringIndex < 4) {
				endErrStringIndex = getStringEnd(buf, 3);
			}
			errorMsg.append(new String(buf, 4, endErrStringIndex - 4));
			return errorCode;
		} else {
			return -1;
		}
	}

	private short ParseRQ(byte[] buf, byte[] fileContentBuf,
			StringBuffer requestedFile) throws TFTPIllegalOperation {
		int endFileStringIndex = -2;
		ByteBuffer rcvWrap = ByteBuffer.wrap(buf);
		short RQ = rcvWrap.getShort();
		if (RQ == OP_RRQ || RQ == OP_WRQ) {
			endFileStringIndex = getStringEnd(buf, 1);
			requestedFile.append(new String(buf, 2, endFileStringIndex - 2));
		} else if (RQ == OP_ERR) {

		} else if (RQ == OP_ACK) {

		} else if (RQ == OP_DAT) {
			// parse block && data
			rcvWrap.position(4);
			rcvWrap.get(fileContentBuf, 0, buf.length - 4);
		} else {
			throw new TFTPIllegalOperation("Illegal TFTP operation");
		}
		if (endFileStringIndex == -1) {
			throw new TFTPIllegalOperation("Illegal TFTP operation");
		}
		return RQ;
	}

	private StringBuffer ParseMOD(byte[] buf) {
		int modStartOffset = getStringEnd(buf, 1) + 1;
		int endModeStringIndex = getStringEnd(buf, 2);
		return new StringBuffer(new String(buf, modStartOffset,
				endModeStringIndex - modStartOffset));
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

	private void HandleRQ(DatagramSocket sendSocket,
			SocketAddress remoteBindPoint, String stringFile, String stringMod,
			int opRrq) {
		if (opRrq == OP_RRQ) {
			HandleRRQ(sendSocket, remoteBindPoint, stringFile, stringMod);
		} else if (opRrq == OP_WRQ) {
			HandleWRQ(sendSocket, remoteBindPoint, stringFile, stringMod);
		} else
			return;

	}

	private void AssertFileNotExist(File file) throws TFTPFileAlreadyExists {
		if (file.exists()) {
			throw new TFTPFileAlreadyExists("File already exists");
		}
	}

	private void HandleWRQ(DatagramSocket sendSocket,
			SocketAddress remoteBindPoint, String stringFile, String stringMod) {
	//	System.out.println("now handling write request");

		File fileWrite = null;
		FileOutputStream out = null;

		try {
			fileWrite = new File(stringFile);
			AssertFileNotExist(fileWrite);
			out = new FileOutputStream(fileWrite);

			byte[] sendAckBuf = new byte[BUFSIZE];
			DatagramPacket sendRQAckPacket = formPacket(OP_ACK, (short) 0,
					(short) -1, sendAckBuf, 0, null, remoteBindPoint);
			sendSocket.send(sendRQAckPacket);

			ReadAndWriteACk(sendSocket, remoteBindPoint, out);

		} catch (AccessControlException e) {
			System.err.println(e.getMessage());
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 2, buf, 0, "File not found", remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}
		} catch (OutOfMemoryError e) {
			System.err.println(e.getMessage());
			// send error message
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 3, buf, 0, e.getMessage(), remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
			// send error message
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 0, buf, 0, e.getMessage(), remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}

		} catch (TFTPUnkownTransferID e) {
			System.err.println(e.getMessage());
			byte[] errBuf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 5, errBuf, 0, e.getMessage(), remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}
		} catch (TFTPIllegalOperation e) {
			System.err.println(e.getMessage());
			byte[] errBuf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 4, errBuf, 0, e.getMessage(), remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}
		} catch (TFTPMaxRetransmissionReached e) {
			System.err.println(e.getMessage());
			// send error message
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 0, buf, 0, e.getMessage(), remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}
		} catch (TFTPFileAlreadyExists e) {
			System.err.println(e.getMessage());
			// send error message
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 6, buf, 0, e.getMessage(), remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
			// send error message
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 0, buf, 0, e.getMessage(), remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}
		} finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (IOException e) {
			}
		}

	}

	private void ReadAndWriteACk(DatagramSocket sendSocket,
			SocketAddress remoteBindPoint, FileOutputStream out)
			throws Exception {
		short block = 1;
		boolean finFlag = false;
		DatagramPacket preSendAckPacket = null;
		while (!finFlag) {
			byte[] sendAckBuf = new byte[BUFSIZE];
			byte[] fileContentBuf = new byte[BUFSIZE - 4];
			byte[] rcvDataBuf = new byte[BUFSIZE];

			DatagramPacket rcvPacket = new DatagramPacket(rcvDataBuf,
					rcvDataBuf.length);

			int base = 1000;
			int iTime = base;
			for (; iTime < 32 * base; iTime *= 2) {
				try {
					sendSocket.setSoTimeout(iTime);
					sendSocket.receive(rcvPacket);

					// Parse packet
					// Check Client SocketAddress
					if (!remoteBindPoint.equals(rcvPacket.getSocketAddress())) {
						throw new TFTPUnkownTransferID("Unknown TFTP ID");
					}

					short opcode = ParseRQ(rcvDataBuf, fileContentBuf, null);
					short rcvBlock = ParseBLK(rcvDataBuf);
					if (opcode == OP_DAT) {
						if (rcvBlock != block) {
							continue;
						}
						finFlag = HandleDAT(fileContentBuf, rcvPacket
								.getLength() - 4, out);
						System.out.println("length:"
								+ (rcvPacket.getLength() - 4)
								+ " ACK block send: " + block);
						DatagramPacket sendAckPacket = formPacket(OP_ACK,
								block++, (short) -1, sendAckBuf, 0, null,
								remoteBindPoint);

						sendSocket.send(sendAckPacket);
						preSendAckPacket = new DatagramPacket(sendAckPacket
								.getData(), sendAckPacket.getLength(),
								sendAckPacket.getSocketAddress());
						break;
					} else if (opcode == OP_ERR) {
						StringBuffer errorMsg = new StringBuffer();
						short errorCode = ParseERR(rcvDataBuf, errorMsg);
						System.err.println("Error code:" + errorCode
								+ " Error Message:" + errorMsg.toString());
						throw new Exception(errorMsg.toString());
					}

				} catch (SocketTimeoutException e) {
					if (preSendAckPacket != null) {
						sendSocket.send(preSendAckPacket);
						continue;
					} else {
						finFlag = true;
						break;
					}
				}
			}
			if (iTime == 32 * base) {
				throw new TFTPMaxRetransmissionReached(
						"Retransmission maximum reached");
			}
		}
		// end loop
	}

	private boolean HandleDAT(byte[] fileContentBuf, int length,
			FileOutputStream out) {

		try {
			out.write(fileContentBuf, 0, length);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (length == (BUFSIZE - 4)) {
			return false;
		}// end of file transmission
		else if (length < (BUFSIZE - 4)) {
			return true;
		} else
			return true;

	}

	private void HandleRRQ(DatagramSocket sendSocket,
			SocketAddress remoteBindPoint, String stringFile, String stringMod) {

//		System.out.println("now handling read request");

		File fileRead = null;
		FileInputStream is = null;

		try {
			fileRead = new File(stringFile);
			is = new FileInputStream(fileRead);
			WriteAndReadACK(sendSocket, is, remoteBindPoint);

		} catch (AccessControlException e) {
			System.err.println("File " + stringFile + " cannnot be found!");
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 2, buf, 0, "File not found", remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}
		} catch (OutOfMemoryError e) {
			System.err.println("File " + stringFile + " cannnot be found!");
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 3, buf, 0, "File not found", remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}
		} catch (FileNotFoundException e) {
			System.err.println("File " + stringFile + " cannnot be found!");
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 1, buf, 0, "File not found", remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}

		} catch (TFTPUnkownTransferID e) {
			System.err.println("File " + stringFile + " cannnot be found!");
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 5, buf, 0, "File not found", remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}

		} catch (IOException e) {
			e.printStackTrace();
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 0, buf, 0, e.getMessage(), remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}
		} catch (TFTPMaxRetransmissionReached e) {
			System.err.println(e.getMessage());
			// send error message
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 0, buf, 0, e.getMessage(), remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
			// send Error message
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket sendErrPacket = formPacket(OP_ERR, (short) -1,
					(short) 0, buf, 0, e.getMessage(), remoteBindPoint);
			try {
				sendSocket.send(sendErrPacket);
			} catch (IOException e1) {
			}
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}

	}

	private void WriteAndReadACK(DatagramSocket sendSocket, FileInputStream is,
			SocketAddress remoteBindPoint) throws Exception {
		int byteRead = 0;
		short block = 1;
		byte[] dataBuf = new byte[BUFSIZE];
		while ((byteRead = is.read(dataBuf, 4, BUFSIZE - 4)) != -1) {
			DatagramPacket sendPacket = formPacket(OP_DAT, block, (short) -1,
					dataBuf, byteRead, null, remoteBindPoint);
			SendAndReadAck(sendSocket, sendPacket, block);
			block++;
		}
	}

	private void SendAndReadAck(DatagramSocket sendSocket,
			DatagramPacket sendPacket, short block) throws Exception {
		byte[] rcvAckBuf = null;
		int base = 1000;
		int iTime = base;
		for (; iTime < 32 * base; iTime *= 2) {
			try {
				sendSocket.send(sendPacket);
				// timeout...
				sendSocket.setSoTimeout(iTime);
				// recevie ACK Packet
				rcvAckBuf = new byte[BUFSIZE];
				DatagramPacket rcvPacket = new DatagramPacket(rcvAckBuf,
						rcvAckBuf.length);
				sendSocket.receive(rcvPacket);

				// check Client SocketAddress
				if (!rcvPacket.getSocketAddress().equals(
						sendPacket.getSocketAddress())) {
					throw new TFTPUnkownTransferID("Unknown TFTP ID");
				}
				short ACK = ParseRQ(rcvAckBuf, null, null);
				short rcvBlock = ParseBLK(rcvAckBuf);
				if (ACK == OP_ACK) {
					if (block == rcvBlock) {
						// System.out.println("ACK block " + block +
						// " received");
						break;
					} else {
						System.err
								.println("Exception:wrong packet received!Retransmission!");
						continue;
					}
				} else if (ACK == OP_ERR) {
					StringBuffer errorMsg = new StringBuffer();
					short errorCode = ParseERR(rcvAckBuf, errorMsg);
					throw new Exception("Error code:" + errorCode
							+ " Error Message:" + errorMsg.toString());
				}
			} catch (SocketTimeoutException e) {
				System.err.println("Time expire!Retransmission block:" + block);
				continue;
			}
		}
		if (iTime == 32 * base) {
			throw new TFTPMaxRetransmissionReached(
					"Retransmission maximum reached");
		}
	}

	private DatagramPacket formPacket(short opcode, short block,
			short errorCode, byte[] buf, int byteRead, String errMsg,
			SocketAddress remoteBindPoint) {

		if (opcode == OP_DAT) {
			return formDataPacket(opcode, block, buf, byteRead, remoteBindPoint);
		} else if (opcode == OP_ACK) {
			return formAckPacket(opcode, block, buf, remoteBindPoint);
		} else if (opcode == OP_ERR) {
			return formErrPacket(opcode, errorCode, buf, errMsg,
					remoteBindPoint);
		}
		return null;
	}

	private DatagramPacket formErrPacket(short opcode, short errorCode,
			byte[] buf, String errMsg, SocketAddress remoteBindPoint) {
		byte endStringByte = 0;
		byte[] errMsgBuf = errMsg.getBytes();
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		wrap.putShort(opcode);
		wrap.putShort(errorCode);
		wrap.put(errMsgBuf);
		wrap.put(endStringByte);
		try {
			DatagramPacket sendPacket = new DatagramPacket(buf,
					5 + errMsgBuf.length, remoteBindPoint);
			return sendPacket;

		} catch (SocketException e) {
			e.printStackTrace();
			return null;
		}

	}

	private DatagramPacket formAckPacket(short opcode, short block, byte[] buf,
			SocketAddress remoteBindPoint) {
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		wrap.putShort(opcode);
		wrap.putShort(block);
		try {
			DatagramPacket sendPacket = new DatagramPacket(buf, 4,
					remoteBindPoint);
			return sendPacket;

		} catch (SocketException e) {
			e.printStackTrace();
			return null;
		}
	}

	private DatagramPacket formDataPacket(short opcode, short block,
			byte[] buf, int byteRead, SocketAddress remoteBindPoint) {
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		wrap.putShort(opcode);
		wrap.putShort(block);
		try {
			DatagramPacket sendPacket = new DatagramPacket(buf, byteRead + 4,
					remoteBindPoint);
			return sendPacket;

		} catch (SocketException e) {
			e.printStackTrace();
			return null;
		}
	}

}
