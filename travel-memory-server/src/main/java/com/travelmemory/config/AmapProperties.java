package com.travelmemory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "amap")
public class AmapProperties {

    private WebService webService = new WebService();
    private ReverseGeocode reverseGeocode = new ReverseGeocode();

    public WebService getWebService() {
        return webService;
    }

    public void setWebService(WebService webService) {
        this.webService = webService;
    }

    public ReverseGeocode getReverseGeocode() {
        return reverseGeocode;
    }

    public void setReverseGeocode(ReverseGeocode reverseGeocode) {
        this.reverseGeocode = reverseGeocode;
    }

    public static class WebService {

        private String key;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    public static class ReverseGeocode {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
