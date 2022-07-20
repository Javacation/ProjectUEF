package org.UEF.others;

/**
 * UEFManager에 전송된 Order의 결과값을 받기위한 클래스<br>
 * setResult를 호출하여 결과값을 지정하면 getResult, getResultNow로 값을 받는다.
 * */
public class ResultWaitter<T> {
	private T result = null;
	private boolean isResultReturn = false;
	
	/**
	 * 해당 객체에 결과값을 삽입하는 메소드로 단 한번만 삽입할 수 있다.(synchronized)
	 * @param result 삽입할 결과 값
	 * */
	synchronized public void setResult(T result){
		if(!isResultReturn) {
			try {
				
				this.result = result;
				isResultReturn = true;
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			finally {
			}
		}	
	}
	
	/**
	 * 결과 값을 리턴하는 메소드로 결과값이 지정될때까지 sleep상태에 들어간다.
	 * @return 결과 값
	 * */
	public T getResult() {
		T tempResult = null;

		
		while(!isResultReturn){
			try {Thread.sleep(1);
			} catch (Exception e) {}
		}

		tempResult = result;

		return tempResult;
	}
	
	/**
	 * 결과 값을 즉시 리턴하는 메소드로 결과 값이 지정되지 않아도 대기상태에 걸리지 않도록 만들었다.
	 * @return 결과 값 (결과 값이 지정되지 않으면 예외발생)
	 * @exception Exception 아직 결과가 지정되지 않음을 알림
	 * */
	public T getResultNow() throws Exception {
		T tempResult = null;
		
		if(isResultReturn) {
			tempResult = result;
		}
		else {
			throw new Exception("No results have been specified yet");
		}
		
		return tempResult;
	}
}
