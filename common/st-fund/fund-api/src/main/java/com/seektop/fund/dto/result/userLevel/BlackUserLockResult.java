package com.seektop.fund.dto.result.userLevel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BlackUserLockResult implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = -8534321919331193207L;

	private Integer successNum = 0;

    private String optData;

    private List<BlackUserLockDetail> details;

}