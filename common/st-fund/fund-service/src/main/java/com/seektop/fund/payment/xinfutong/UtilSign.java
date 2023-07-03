/**
 * 
 */
/**
 * @author Administrator
 *
 */
package com.seektop.fund.payment.xinfutong;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UtilSign{
	
	public static String GetSHAstr(Map<String,String> Parm,String Key){		
		if(Parm.containsKey("sign")){
			Parm.remove("sign");//sign
		}
		List<String> SortStr = Ksort(Parm);
		String SHAStr = CreateLinkstring(Parm,SortStr);
		//Log.Write("SHA拼接字串（不包含密码）：  "+SHAStr);
		//Log.Write("SHA拼接： "+SHAStr+Key);
		return SHAUtils.sign(SHAStr+Key, "utf-8");
	}
	public static String GetMD5str(Map<String,String> Parm,String Key){
		if(Parm.containsKey("sign")){
			Parm.remove("sign");//sign
		}
		List<String> SortStr = Ksort(Parm);
		String SHAStr = CreateLinkstring(Parm,SortStr);
		//Log.Write("MD5拼接字串（不包含密码）：  "+SHAStr);
		//Log.Write("MD5拼接： "+SHAStr+Key);
		return MD5(SHAStr+Key, "utf-8");
	}

	/**
	 * 排序  (升序)
	 * @param Parm
	 * @return
	 */
	public static List<String> Ksort(Map<String,String> Parm){ 
		List<String> SMapKeyList = new ArrayList<String>(Parm.keySet()); 
		Collections.sort(SMapKeyList);
		return SMapKeyList;
	}

	/**
	 * 判断值是否为空 FALSE 为不空  TRUE 为空
	 * @param Temp
	 * @return
	 */
	public static boolean StrEmpty(String Temp){
		if(null == Temp || Temp.isEmpty()){
			return true;
		}
		return false;
	}

	/**
	 * 拼接报文
	 * @param Parm
	 * @param SortStr
	 * @return
	 */
	public static String CreateLinkstring(Map<String,String> Parm,List<String> SortStr){
		String LinkStr = "";
		for(int i=0;i<SortStr.size();i++){
			if(!StrEmpty(Parm.get(SortStr.get(i).toString()))){
				LinkStr += SortStr.get(i) +"="+Parm.get(SortStr.get(i).toString());
				if((i+1)<SortStr.size()){
					LinkStr +="&";
				}
			}
		}
		return LinkStr;
	}
	public static String MD5(String data, String inputCharset) {
		char hexDigits[] = {'0', '1', '2', '3', '4',
				'5', '6', '7', '8', '9',
				'A', 'B', 'C', 'D', 'E', 'F'};
		try {
			byte[] btInput = data.getBytes(inputCharset == null || inputCharset.equals("") ? "utf-8" : inputCharset);
			//获得MD5摘要算法的 MessageDigest 对象
			MessageDigest mdInst = MessageDigest.getInstance("MD5");
			//使用指定的字节更新摘要
			mdInst.update(btInput);
			//获得密文
			byte[] md = mdInst.digest();
			//把密文转换成十六进制的字符串形式
			int j = md.length;
			char str[] = new char[j * 2];
			int k = 0;
			for (int i = 0; i < j; i++) {
				byte byte0 = md[i];
				str[k++] = hexDigits[byte0 >>> 4 & 0xf];
				str[k++] = hexDigits[byte0 & 0xf];
			}

			return new String(str).toLowerCase();

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}