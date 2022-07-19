/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 *
 * @author TRUONGNNT
 * @param <T>
 */
@Getter
@SuperBuilder(builderMethodName = "baseBuilder")
public class ISMessage<T> {

    private CmdType cmdType;
    private String requestID;
    private int timeOutInterval;
    
    @Builder.Default
    private T data = null;

}
