package dv201.labb4;

public class TFTPDskFullorAllocExceed extends Exception {
	private String errorMsg=null;
	public TFTPDskFullorAllocExceed(String message){
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
