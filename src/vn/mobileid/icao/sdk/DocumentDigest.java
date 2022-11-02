/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author acer
 */
@Builder
@Getter
@Setter
public class DocumentDigest {
    private String digestAlgo;
    private String digestValue;
}
