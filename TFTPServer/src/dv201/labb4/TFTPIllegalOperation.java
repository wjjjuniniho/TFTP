package dv201.labb4;

public class TFTPIllegalOperation extends Exception {
	private String errMsg=null;
	public TFTPIllegalOperation(String message){
		errMsg=message;
	}
	
	public String getMessage(){
		return errMsg;
	}
}
