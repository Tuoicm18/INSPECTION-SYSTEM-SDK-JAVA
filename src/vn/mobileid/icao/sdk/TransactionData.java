/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk;

import lombok.Builder;
import lombok.Getter;

/**
 *
 * @author acer
 */
@Builder
@Getter
public class TransactionData {
    private String authorizationTitle;
    private AuthorizationElement[] authContentList;
    private AuthorizationElement[] multipleSelectList;
    private AuthorizationElement[] singleSelectList;
    private AuthorizationElement[] nameValuePairList;
}
