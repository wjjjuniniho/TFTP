package dv201.labb4;

public class TFTPFileAlreadyExists extends Exception {
	private String errorMsg=null;
	public TFTPFileAlreadyExists(String message){
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
