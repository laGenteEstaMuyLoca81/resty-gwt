package org.fusesource.restygwt.client.intercept;


import com.google.gwt.json.client.JSONValue;

public class JsonDecoderInterceptorTestCallback implements JsonDecoderInterceptorCallback<ResponseInterceptedDto> {

    public static final JsonDecoderInterceptorCallback<ResponseInterceptedDto> INSTANCE =
            new JsonDecoderInterceptorTestCallback();

    /**
     * property for testing purpose
     */
    private String lastInput;

    /**
     * property for testing purpose
     */
    private Class<ResponseInterceptedDto> lastType;

    @Override
    public void intercept(JSONValue input, Class<ResponseInterceptedDto> expectedType) {
        lastInput = input.toString();
        lastType = expectedType;
    }

    public String getLastInput() {
        return lastInput;
    }

    public Class<ResponseInterceptedDto> getLastType() {
        return lastType;
    }
}