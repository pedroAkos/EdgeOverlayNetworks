package utils;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

import java.io.File;

public class ExistsFileValidor implements IParameterValidator {
    @Override
    public void validate(String key, String value) throws ParameterException {
        File f = new File(value);
        if(!f.exists())
            throw new ParameterException("File " + value + " does not exist for parameter " + key);
    }
}
