package dv201.labb4;

public class TFTPUnkownTransferID extends Exception {

	private String errMsg=null;
	public TFTPUnkownTransferID(String message){
		errMsg=message;
	}
	
	public String getMessage(){
		return errMsg;
	}
}
