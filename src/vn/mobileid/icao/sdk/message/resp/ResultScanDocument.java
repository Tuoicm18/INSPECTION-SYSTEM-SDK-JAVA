/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk.message.resp;

import lombok.Builder;
import lombok.Getter;
import vn.mobileid.icao.sdk.ScanType;

/**
 *
 * @author acer
 */
@Builder
@Getter
public class ResultScanDocument {
    private ScanType scanType;
    private String document;
}
