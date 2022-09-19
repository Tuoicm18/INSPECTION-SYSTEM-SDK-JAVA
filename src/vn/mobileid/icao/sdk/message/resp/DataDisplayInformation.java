/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk.message.resp;

import lombok.Getter;
import lombok.Setter;
import vn.mobileid.icao.sdk.DisplayType;
/**
 *
 * @author acer
 */
@Getter
@Setter
public class DataDisplayInformation {
    private String title;
    private DisplayType type;
    private String value;
}
