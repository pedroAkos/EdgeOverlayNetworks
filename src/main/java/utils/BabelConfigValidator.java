package utils;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

public class BabelConfigValidator implements IParameterValidator {
    @Override
    public void validate(String key, String value) throws ParameterException {
        String[] list = value.split(",");
        for (String prop : list) {
            String[] split = prop.split("=");
            if (split.length != 2) {
                throw new ParameterException("Value " + prop + " is invalid for parameter " + key + " should be in format prop=value");
            }
        }
    }
}
