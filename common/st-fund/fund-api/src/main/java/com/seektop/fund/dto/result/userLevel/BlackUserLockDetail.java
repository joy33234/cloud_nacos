package com.seektop.fund.dto.result.userLevel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NotNull
public class BlackUserLockDetail implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 2185918661794779025L;

	private Integer userId;

    private Integer levelId;

    private String levelName;

}