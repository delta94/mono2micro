import React from 'react';
import { Link } from 'react-router-dom';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';

export class Header extends React.Component {
    render() {
        return (
            <Navbar bg="dark" variant="dark">
                <Navbar.Brand>
                    <Link to='/'>
                        Mono2Micro
                    </Link>
                </Navbar.Brand>
                <Navbar.Toggle />
                <Navbar.Collapse>
                    <Nav>
                        <Nav.Link href="/codebases">
                            Codebases
                        </Nav.Link>
                        <Nav.Link href="/analysis">
                            Microservice Analysis
                        </Nav.Link>
                        <Nav.Link href="/analyser">
                            Analyser
                        </Nav.Link>
                    </Nav>
                </Navbar.Collapse>
            </Navbar>
        );
    }
}
