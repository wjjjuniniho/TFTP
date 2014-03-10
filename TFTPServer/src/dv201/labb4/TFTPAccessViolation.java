package dv201.labb4;

public class TFTPAccessViolation extends Exception {
	private String errorMsg=null;
	public TFTPAccessViolation(String message){
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
