package dv201.labb4;

public class TFTPMaxRetransmissionReached extends Exception {
	private String errorMsg=null;
	public TFTPMaxRetransmissionReached(String message){
		errorMsg=message;
	}
	
	public String getMessage(){
		if(errorMsg!=null){
			return errorMsg;
		}else{
			return this.toString();
		}

	}
}
