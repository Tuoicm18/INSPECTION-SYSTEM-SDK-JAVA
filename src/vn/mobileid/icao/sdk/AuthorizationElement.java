/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 *
 * @author TRUONGNNT
 */
@Builder
@Getter
public class AuthorizationElement {
    private AuthElementType type;
    private int ordinary;
    private String label;
    private String title;
    private String text;
    
    private Map<String, Boolean> multipleSelect;
    private Map<String, Boolean> singleSelect;   
    private Map<String, String> nameValuePair;
    private DocumentDigest documentDigest;
    
}
