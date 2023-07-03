package com.seektop.common.csvexport.annotation;




import com.seektop.common.csvexport.CsvExportComponent;
import com.seektop.common.csvexport.helper.ExportHelper;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({CsvExportComponent.class, ExportHelper.class})
public @interface EnAbleExportUtils {
}
