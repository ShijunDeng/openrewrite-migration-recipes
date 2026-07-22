package com.huawei.clouds.openrewrite.mybatisspring;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class MyBatisSpringXmlMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateMyBatisSpringXml());
    }

    @Test
    void normalizesVersionedSchemaFromRealRepository() {
        // Reduced from 632team/EasyHousing at 5362a94a:
        // https://github.com/632team/EasyHousing/blob/5362a94acc5d792ece4e4b3afdb827e415b98cc9/src/main/resources/config/bean.xml
        rewriteRun(xml(
                """
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xmlns:mybatis="http://mybatis.org/schema/mybatis-spring"
                       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
                                           http://mybatis.org/schema/mybatis-spring http://mybatis.org/schema/mybatis-spring-1.2.xsd">
                    <mybatis:scan base-package="com.housing.mapper"/>
                </beans>
                """,
                """
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xmlns:mybatis="http://mybatis.org/schema/mybatis-spring"
                       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
                                           http://mybatis.org/schema/mybatis-spring http://mybatis.org/schema/mybatis-spring.xsd">
                    <mybatis:scan base-package="com.housing.mapper"/>
                </beans>
                """
        ));
    }

    @Test
    void replacesExplicitScannerObjectReferencesWithBeanNames() {
        rewriteRun(xml(
                """
                <beans>
                    <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
                        <property name="basePackage" value="example.mapper"/>
                        <property name="sqlSessionFactory" ref="ordersSqlSessionFactory"/>
                        <property name="sqlSessionTemplate" ref="ordersSqlSessionTemplate"/>
                    </bean>
                </beans>
                """,
                """
                <beans>
                    <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
                        <property name="basePackage" value="example.mapper"/>
                        <property name="sqlSessionFactoryBeanName" value="ordersSqlSessionFactory"/>
                        <property name="sqlSessionTemplateBeanName" value="ordersSqlSessionTemplate"/>
                    </bean>
                </beans>
                """
        ));
    }

    @Test
    void leavesAlreadyCompatibleAndAmbiguousScannerPropertiesUntouched() {
        rewriteRun(xml(
                """
                <beans>
                    <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
                        <property name="sqlSessionFactoryBeanName" value="ordersSqlSessionFactory"/>
                        <property name="sqlSessionTemplate" value="#{sessionTemplate}"/>
                    </bean>
                    <bean class="org.mybatis.spring.mapper.MapperFactoryBean">
                        <property name="sqlSessionFactory" ref="ordersSqlSessionFactory"/>
                    </bean>
                </beans>
                """
        ));
    }

    @Test
    void marksAmbiguousScannerReferences() {
        rewriteRun(
                spec -> spec.recipe(new FindMyBatisSpringXmlRisks()),
                xml(
                        """
                        <beans xmlns:mybatis="http://mybatis.org/schema/mybatis-spring">
                            <mybatis:scan base-package="example.mapper"
                                          factory-ref="ordersFactory" template-ref="ordersTemplate"/>
                        </beans>
                        """,
                        """
                        <beans xmlns:mybatis="http://mybatis.org/schema/mybatis-spring">
                            <!--~~(mybatis:scan specifies both factory-ref and template-ref; select one session boundary explicitly)~~>--><mybatis:scan base-package="example.mapper"
                                          factory-ref="ordersFactory" template-ref="ordersTemplate"/>
                        </beans>
                        """
                )
        );
    }

    @Test
    void marksConflictingSqlSessionFactoryConfiguration() {
        rewriteRun(
                spec -> spec.recipe(new FindMyBatisSpringXmlRisks()),
                xml(
                        """
                        <beans>
                            <bean class="org.mybatis.spring.SqlSessionFactoryBean">
                                <property name="configuration" ref="mybatisConfiguration"/>
                                <property name="configLocation" value="classpath:mybatis-config.xml"/>
                            </bean>
                        </beans>
                        """,
                        """
                        <beans>
                            <!--~~(SqlSessionFactoryBean forbids configuration and configLocation together; choose programmatic or XML configuration)~~>--><bean class="org.mybatis.spring.SqlSessionFactoryBean">
                                <property name="configuration" ref="mybatisConfiguration"/>
                                <property name="configLocation" value="classpath:mybatis-config.xml"/>
                            </bean>
                        </beans>
                        """
                )
        );
    }

    @Test
    void marksSpringBatchXmlNamespaceButNotMyBatisNamespace() {
        rewriteRun(
                spec -> spec.recipe(new FindMyBatisSpringXmlRisks()),
                xml(
                        """
                        <beans xmlns:batch="http://www.springframework.org/schema/batch"
                               xmlns:mybatis="http://mybatis.org/schema/mybatis-spring">
                            <batch:job id="importJob"/>
                            <mybatis:scan base-package="example.mapper"/>
                        </beans>
                        """,
                        """
                        <beans <!--~~(Spring Batch 6 deprecates its XML namespace; migrate job infrastructure to Java configuration)~~>-->xmlns:batch="http://www.springframework.org/schema/batch"
                               xmlns:mybatis="http://mybatis.org/schema/mybatis-spring">
                            <batch:job id="importJob"/>
                            <mybatis:scan base-package="example.mapper"/>
                        </beans>
                        """
                )
        );
    }

    @Test
    void marksOnlyScannerPropertiesThatCouldNotBeConverted() {
        rewriteRun(
                spec -> spec.recipes(new MigrateMyBatisSpringXml(), new FindMyBatisSpringXmlRisks()),
                xml(
                        """
                        <beans>
                            <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
                                <property name="sqlSessionFactory" value="#{factory}"/>
                                <property name="sqlSessionTemplate" ref="sessionTemplate"/>
                            </bean>
                        </beans>
                        """,
                        """
                        <beans>
                            <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
                                <!--~~(Deprecated scanner object property could not be converted; provide an explicit ref and use the corresponding BeanName property)~~>--><property name="sqlSessionFactory" value="#{factory}"/>
                                <property name="sqlSessionTemplateBeanName" value="sessionTemplate"/>
                            </bean>
                        </beans>
                        """
                )
        );
    }

    @Test
    void leavesNonConflictingXmlUnmarked() {
        rewriteRun(
                spec -> spec.recipe(new FindMyBatisSpringXmlRisks()),
                xml(
                        """
                        <beans xmlns:mybatis="http://mybatis.org/schema/mybatis-spring">
                            <mybatis:scan base-package="example.mapper" factory-ref="ordersFactory"/>
                            <bean class="org.mybatis.spring.SqlSessionFactoryBean">
                                <property name="configuration" ref="mybatisConfiguration"/>
                            </bean>
                        </beans>
                        """
                )
        );
    }
}
