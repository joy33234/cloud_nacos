/**
 * @author Administrator
 * 打印参数
 */
package com.seektop.fund.payment.xinfutong;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Log{
	
	public static void Write(String LogStr){		
		System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\t: " + LogStr);		
	}
}