package org.codehaus.plexus.spring;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.LoggerManager;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.springframework.beans.BeansException;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.util.ReflectionUtils;

/**
 * A FactoryBean dedicated to building plexus components. This includes :
 * <ul>
 * <li>Support for direct field injection or "requirements"</li>
 * <li>Support for LogEnabled, Initializable and Disposable plexus interfaces</li>
 * <li>Support for plexus.requirement to get a Map<role-hint, component> for a
 * role
 * </ul>
 * If not set, the beanFActory will auto-detect the loggerManager to use by
 * searching for the adequate bean in the spring context.
 * <p>
 *
 * @author <a href="mailto:nicolas@apache.org">Nicolas De Loof</a>
 */
public class PlexusComponentFactoryBean
    implements FactoryBean, BeanFactoryAware, DisposableBean
{
    /** Logger available to subclasses */
    protected final Log logger = LogFactory.getLog( getClass() );

    /** The beanFactory */
    private BeanFactory beanFactory;

    /**
     * @todo isn't there a constant for this in plexus ?
     */
    private static final String SINGLETON = "singleton";

    /** The plexus component role */
    private Class role;

    /** The plexus component implementation class */
    private Class implementation;

    /** The plexus component instantiation strategy */
    private String instantiationStrategy = SINGLETON;

    /** The plexus component requirements and configurations */
    private Map requirements;

    /** The plexus component created by this FactoryBean */
    private List instances = new LinkedList();

    /** Optional plexus loggerManager */
    private static LoggerManager loggerManager;

    /** Optional plexus context */
    private static Context context;

    /**
     * {@inheritDoc}
     *
     * @see org.springframework.beans.factory.config.AbstractFactoryBean#destroy()
     */
    public void destroy()
        throws Exception
    {
        synchronized ( instances )
        {
            for ( Iterator iterator = instances.iterator(); iterator.hasNext(); )
            {
                Object isntance = iterator.next();
                if ( isntance instanceof Disposable )
                {
                    ( (Disposable) isntance ).dispose();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     * @see org.springframework.beans.factory.FactoryBean#getObject()
     */
    public Object getObject()
        throws Exception
    {
        if ( isSingleton() && !instances.isEmpty())
        {
            return instances.get( 0 );
        }
        return createInstance();
    }

    /**
     * Create the plexus component instance. Inject dependencies declared as
     * requirements using direct field injection
     */
    public Object createInstance()
        throws Exception
    {
        logger.debug( "Creating plexus component " + implementation );
        final Object component = implementation.newInstance();
        synchronized ( instances )
        {
            instances.add( component );
        }
        if ( requirements != null )
        {
            for ( Iterator iterator = requirements.entrySet().iterator(); iterator.hasNext(); )
            {
                Map.Entry requirement = (Map.Entry) iterator.next();
                String fieldName = (String) requirement.getKey();

                if ( fieldName.startsWith( "#" ) )
                {
                    // implicit field injection : the field name was no
                    // specified in the plexus descriptor as only one filed
                    // matches Dependency type

                    RuntimeBeanReference ref = (RuntimeBeanReference) requirement.getValue();
                    Object dependency = beanFactory.getBean( ref.getBeanName() );

                    Field[] fields = implementation.getDeclaredFields();
                    for ( int i = 0; i < fields.length; i++ )
                    {
                        Field field = fields[i];
                        if ( ReflectionUtils.COPYABLE_FIELDS.matches( field )
                            && field.getType().isAssignableFrom( dependency.getClass() ) )
                        {
                            if ( logger.isTraceEnabled() )
                            {
                                logger.trace( "Injecting dependency " + dependency + " into field " + field.getName() );
                            }
                            ReflectionUtils.makeAccessible( field );
                            ReflectionUtils.setField( field, component, dependency );
                        }
                    }
                }
                else
                {
                    // explicit field injection
                    fieldName = PlexusToSpringUtils.toCamelCase( fieldName );
                    Field field = findField( fieldName );
                    Object dependency = resolveRequirement( field, requirement.getValue() );
                    if ( logger.isTraceEnabled() )
                    {
                        logger.trace( "Injecting dependency " + dependency + " into field " + field.getName() );
                    }
                    ReflectionUtils.makeAccessible( field );
                    ReflectionUtils.setField( field, component, dependency );
                }
            }
        }

        handlePlexusLifecycle( component );

        return component;
    }

    private Field findField( String fieldName )
    {
        Class clazz = implementation;
        while (clazz != Object.class)
        {
            try
            {
                return clazz.getDeclaredField( fieldName );
            }
            catch (NoSuchFieldException e)
            {
                clazz = clazz.getSuperclass();
            }
        }
        String error = "No field " + fieldName + " on implementation class " + implementation;
        logger.error( error );
        throw new BeanInitializationException( error );
    }

    private void handlePlexusLifecycle( final Object component )
        throws ContextException, InitializationException
    {
        if ( component instanceof LogEnabled )
        {
            ( (LogEnabled) component ).enableLogging( getLoggerManager().getLoggerForComponent( role.getName() ) );
        }

        if ( component instanceof Contextualizable )
        {
            // VERRY limited support for Contextualizable
            ( (Contextualizable) component ).contextualize( getContext() );
        }

        // TODO add support for Startable, Stopable -> LifeCycle ?

        if ( component instanceof Initializable )
        {
            ( (Initializable) component ).initialize();
        }
    }

    /**
     * Resolve the requirement that this field exposes in the component
     *
     * @param field
     * @return
     */
    protected Object resolveRequirement( Field field, Object requirement )
    {
        if ( requirement instanceof RuntimeBeanReference )
        {
            String beanName = ( (RuntimeBeanReference) requirement ).getBeanName();
            if ( Map.class.isAssignableFrom( field.getType() ) )
            {
                // component ask plexus for a Map of all available
                // components for the role
                requirement = PlexusToSpringUtils.lookupMap( beanName, getListableBeanFactory() );
            }
            else if ( Collection.class.isAssignableFrom( field.getType() ) )
            {
                requirement = PlexusToSpringUtils.LookupList( beanName, getListableBeanFactory() );
            }
            else
            {
                requirement = beanFactory.getBean( beanName );
            }
        }
        if ( requirement != null )
        {
            requirement = getBeanTypeConverter().convertIfNecessary( requirement, field.getType() );
        }
        return requirement;

    }

    public Class getObjectType()
    {
        return role;
    }

    public boolean isSingleton()
    {
        return SINGLETON.equals( instantiationStrategy );
    }

    /**
     * @return
     */
    protected Context getContext()
    {
        if ( context == null )
        {
            PlexusContainer container = (PlexusContainer) beanFactory.getBean( "plexusContainer" );
            context = container.getContext();
        }
        return context;
    }

    protected TypeConverter getBeanTypeConverter()
    {
        if ( beanFactory instanceof ConfigurableBeanFactory )
        {
            return ( (ConfigurableBeanFactory) beanFactory ).getTypeConverter();
        }
        else
        {
            return new SimpleTypeConverter();
        }
    }

    /**
     * Retrieve the loggerManager instance to be used for LogEnabled components
     *
     * @return
     */
    protected LoggerManager getLoggerManager()
    {
        if ( loggerManager == null )
        {
            if ( beanFactory.containsBean( "loggerManager" ) )
            {
                loggerManager = (LoggerManager) beanFactory.getBean( "loggerManager" );
            }
            else
            {
                Map loggers = getListableBeanFactory().getBeansOfType( LoggerManager.class );
                if ( loggers.size() == 1 )
                {
                    loggerManager = (LoggerManager) loggers.values().iterator().next();
                }
            }
        }
        if ( loggerManager == null )
        {
            throw new BeanCreationException( "A LoggerManager instance must be set in the applicationContext" );
        }
        return loggerManager;
    }

    private ListableBeanFactory getListableBeanFactory()
    {
        if ( beanFactory instanceof ListableBeanFactory )
        {
            return (ListableBeanFactory) beanFactory;
        }
        throw new BeanInitializationException( "A ListableBeanFactory is required by the PlexusComponentFactoryBean" );
    }

    /**
     * @param loggerManager the loggerManager to set
     */
    public void setLoggerManager( LoggerManager loggerManager )
    {
        PlexusComponentFactoryBean.loggerManager = loggerManager;
    }

    /**
     * @param role the role to set
     */
    public void setRole( Class role )
    {
        this.role = role;
    }

    /**
     * @param implementation the implementation to set
     */
    public void setImplementation( Class implementation )
    {
        this.implementation = implementation;
    }

    /**
     * @param instanciationStrategy the instanciationStrategy to set
     */
    public void setInstanciationStrategy( String instanciationStrategy )
    {
        if ( instanciationStrategy.length() == 0 )
        {
            instanciationStrategy = SINGLETON;
        }
        if ( "poolable".equals( instanciationStrategy ) )
        {
            throw new BeanCreationException( "Plexus poolable instanciation-strategy is not supported" );
        }
        this.instantiationStrategy = instanciationStrategy;
    }

    /**
     * @param requirements the requirements to set
     */
    public void setRequirements( Map requirements )
    {
        this.requirements = requirements;
    }

    public void setContext( Context context )
    {
        PlexusComponentFactoryBean.context = context;
    }

    public void setBeanFactory( BeanFactory beanFactory )
    {
        this.beanFactory = beanFactory;
    }

}
