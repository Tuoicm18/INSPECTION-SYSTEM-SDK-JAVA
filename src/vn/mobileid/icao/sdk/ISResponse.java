/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk;

import java.util.Optional;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 *
 * @author TRUONGNNT
 */
@SuperBuilder
@Getter
class ISResponse<T> extends ISMessage<Optional<T>> {

    private int errorCode;
    private String errorMessage;

}
