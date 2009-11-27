/*
 * Copyright 2007-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.binding;

import groovy.lang.MissingMethodException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;


/**
 * @author <a href="mailto:shemnon@yahoo.com">Danno Ferrin</a>
 * @version $Revision$
 * @since Groovy 1.1
 */
public class PropertyBinding implements SourceBinding, TargetBinding, TriggerBinding {

    Object bean;
    String propertyName;
    boolean nonChangeCheck;

    public PropertyBinding(Object bean, String propertyName) {
        this.bean = bean;
        this.propertyName = propertyName;
    }

    public void updateTargetValue(Object newValue) {
        if (nonChangeCheck) {
            if (DefaultTypeTransformation.compareEqual(getSourceValue(), newValue)) {
                // not a change, don't fire it
                return;
            }
        }
        try {
            InvokerHelper.setProperty(bean, propertyName, newValue);
        } catch (InvokerInvocationException iie) {
            if (!(iie.getCause() instanceof PropertyVetoException)) {
                throw iie;
            }
            // ignore veto exceptions, just let the binding fail like a validaiton does
        }
    }

    public boolean isNonChangeCheck() {
        return nonChangeCheck;
    }

    public void setNonChangeCheck(boolean nonChangeCheck) {
        this.nonChangeCheck = nonChangeCheck;
    }

    public Object getSourceValue() {
        return InvokerHelper.getPropertySafe(bean, propertyName);
    }

    public FullBinding createBinding(SourceBinding source, TargetBinding target) {
        return new PropertyFullBinding(source, target);
    }

    class PropertyFullBinding extends AbstractFullBinding implements PropertyChangeListener {

        Object boundBean;
        Object boundProperty;
        boolean bound;
        boolean boundToProperty;

        PropertyFullBinding(SourceBinding source, TargetBinding target) {
            setSourceBinding(source);
            setTargetBinding(target);
        }

        public void propertyChange(PropertyChangeEvent event) {
            if (boundToProperty || event.getPropertyName().equals(boundProperty)) {
                update();
            }
        }

        public void bind() {
            if (!bound) {
                bound = true;
                boundBean = bean;
                boundProperty = propertyName;
                try {
                    InvokerHelper.invokeMethodSafe(boundBean, "addPropertyChangeListener", new Object[] {boundProperty, this});
                    boundToProperty = true;
                } catch (MissingMethodException mme) {
                    try {
                        boundToProperty = false;
                        InvokerHelper.invokeMethodSafe(boundBean, "addPropertyChangeListener", new Object[] {this});
                    } catch (MissingMethodException mme2) {
                        throw new RuntimeException("Properties in beans of type " + bean.getClass().getName() + " are not observable in any capacity (no PropertyChangeListener support).");
                    }
                }
            }
        }

        public void unbind() {
            if (bound) {
                if (boundToProperty) {
                    try {
                        InvokerHelper.invokeMethodSafe(boundBean, "removePropertyChangeListener", new Object[] {boundProperty, this});
                    } catch (MissingMethodException mme) {
                        // ignore, too bad so sad they don't follow conventions, we'll just leave the listener attached
                    }
                } else {
                    try {
                        InvokerHelper.invokeMethodSafe(boundBean, "removePropertyChangeListener", new Object[] {this});
                    } catch (MissingMethodException mme2) {
                        // ignore, too bad so sad they don't follow conventions, we'll just leave the listener attached
                    }
                }
                boundBean = null;
                boundProperty = null;
                bound = false;
            }
        }

        public void rebind() {
            if (bound) {
                unbind();
                bind();
            }
        }
    }

    public Object getBean() {
        return bean;
    }

    public void setBean(Object bean) {
        this.bean = bean;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }
}
