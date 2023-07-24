+ 如果不需要再应用启动的时候加载模板，直接使用ParserUtils
+ 如果需要在应用启动的时候加载模板到内存，
++ 需要重写FreemarkerService
++ 在启动类加上@EnableFreemarker